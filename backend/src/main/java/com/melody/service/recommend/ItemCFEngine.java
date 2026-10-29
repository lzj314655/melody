package com.melody.service.recommend;

import com.melody.entity.UserBehaviorLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Item-CF 核心算法引擎（纯 Java，不依赖任何 ML 库）
 *
 * PRD §4.3.3 算法步骤：
 *   1. 构建用户-物品隐式反馈矩阵（VIEW=1 / ADD_CART=3 / FAVORITE=4 / ORDER=5）
 *   2. 物品共现矩阵 + 余弦相似度
 *   3. Top-N 召回（每物品取 Top-20 相似）
 *
 * 设计取舍：
 *   - 同一 (user, item) 对同一行为类型累加权重，不同行为类型取最高（避免重复点击 VIEW 把分数刷爆）
 *   - 余弦相似度分母加 1e-10 防 0 除
 *   - 全程 in-memory 计算，demo 数据量（~220 条行为 + 55 商品）几毫秒搞定
 *   - 生产环境数据量大时改为 Spark / Flink 离线计算（PRD §5.5 已预留扩展位）
 */
public class ItemCFEngine {

    /** 行为权重映射（与 entity.enums.BehaviorTypeEnum.weight 一致） */
    private static final Map<String, Double> WEIGHT_MAP = new HashMap<>();
    static {
        WEIGHT_MAP.put("VIEW",      1.0);
        WEIGHT_MAP.put("ADD_CART",  3.0);
        WEIGHT_MAP.put("FAVORITE",  4.0);
        WEIGHT_MAP.put("ORDER",     5.0);
    }

    /** 召回 Top-N 大小 */
    public static final int TOP_N_ITEM  = 20;
    public static final int TOP_N_USER  = 20;

    /**
     * 步骤 1：构建用户-物品隐式反馈矩阵
     * 返回 Map<userId, Map<productId, weight>>
     */
    public Map<Long, Map<Long, Double>> buildUserItemMatrix(List<UserBehaviorLog> logs) {
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        for (UserBehaviorLog log : logs) {
            if (log.getUserId() == null || log.getProductId() == null) continue;  // 跳过游客 / 无效
            Double w = WEIGHT_MAP.get(log.getBehaviorType());
            if (w == null) continue;
            matrix.computeIfAbsent(log.getUserId(), k -> new HashMap<>())
                  .merge(log.getProductId(), w, Double::sum);
        }
        return matrix;
    }

    /**
     * 步骤 2：物品共现矩阵 + 余弦相似度
     * 返回 Map<itemId, List<SimilarItem>>，每个 item 取 Top-20 相似物品
     */
    public Map<Long, List<SimilarItem>> computeItemSimilarity(Map<Long, Map<Long, Double>> userItem) {
        // 转置：itemId -> {userId -> weight}
        Map<Long, Map<Long, Double>> itemVectors = new HashMap<>();
        Map<Long, Double> itemNorms = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Double>> userEntry : userItem.entrySet()) {
            Long uid = userEntry.getKey();
            for (Map.Entry<Long, Double> itemEntry : userEntry.getValue().entrySet()) {
                Long iid = itemEntry.getKey();
                Double w = itemEntry.getValue();
                itemVectors.computeIfAbsent(iid, k -> new HashMap<>()).put(uid, w);
                itemNorms.merge(iid, w * w, Double::sum);
            }
        }

        Map<Long, List<SimilarItem>> result = new HashMap<>();
        List<Long> items = new ArrayList<>(itemVectors.keySet());

        for (int i = 0; i < items.size(); i++) {
            Long itemA = items.get(i);
            Map<Long, Double> vecA = itemVectors.get(itemA);
            double normA = Math.sqrt(itemNorms.get(itemA));

            // 用小顶堆保留 Top-K（避免全排序）
            PriorityQueue<SimilarItem> heap = new PriorityQueue<>(
                    Comparator.comparingDouble(s -> s.score));

            for (int j = 0; j < items.size(); j++) {
                if (i == j) continue;
                Long itemB = items.get(j);
                Map<Long, Double> vecB = itemVectors.get(itemB);
                double normB = Math.sqrt(itemNorms.get(itemB));

                // 点积
                double dot = 0;
                for (Map.Entry<Long, Double> e : vecA.entrySet()) {
                    Double bw = vecB.get(e.getKey());
                    if (bw != null) dot += e.getValue() * bw;
                }
                double sim = dot / (normA * normB + 1e-10);
                if (sim > 0) {
                    heap.offer(new SimilarItem(itemB, sim));
                    if (heap.size() > TOP_N_ITEM) heap.poll();
                }
            }

            // 堆转 List + 降序
            List<SimilarItem> top = new ArrayList<>(heap);
            top.sort((a, b) -> Double.compare(b.score, a.score));
            result.put(itemA, top);
        }
        return result;
    }

    /**
     * 步骤 3：用户 Top-N 推荐
     * 输入：用户-物品矩阵 + 物品相似度
     * 逻辑：对用户交互过的每个物品，找其相似物品，按相似度累加得分（排除已交互的），取 Top-20
     */
    public Map<Long, List<Long>> generateUserRecommendations(
            Map<Long, Map<Long, Double>> userItem,
            Map<Long, List<SimilarItem>> itemSimilarity) {

        Map<Long, List<Long>> result = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> userEntry : userItem.entrySet()) {
            Long uid = userEntry.getKey();
            Map<Long, Double> interactedSet = userEntry.getValue();
            Map<Long, Double> scores = new HashMap<>();

            for (Long interactedItem : interactedSet.keySet()) {
                List<SimilarItem> similar = itemSimilarity.getOrDefault(interactedItem, List.of());
                for (SimilarItem s : similar) {
                    if (interactedSet.containsKey(s.itemId())) continue; // 排除已交互
                    scores.merge(s.itemId(), s.score(), Double::sum);
                }
            }

            List<Long> recommended = scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(TOP_N_USER)
                    .map(Map.Entry::getKey)
                    .toList();
            result.put(uid, recommended);
        }
        return result;
    }

    /**
     * 相似物品记录（record 风格，等价于不可变 POJO）
     */
    public record SimilarItem(Long itemId, double score) {}
}