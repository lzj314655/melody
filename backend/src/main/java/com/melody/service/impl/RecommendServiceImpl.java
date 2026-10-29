package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TProduct;
import com.melody.entity.UserBehaviorLog;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.UserBehaviorLogMapper;
import com.melody.service.IProductService;
import com.melody.service.IRecommendService;
import com.melody.service.recommend.BehaviorScopes;
import com.melody.service.recommend.ItemCFEngine;
import com.melody.service.recommend.ItemCFEngine.SimilarItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 推荐 Service 实现
 *
 * 读路径：先查 Redis → 命中即返回；未命中走冷启动兜底
 * 写路径：由 RecommendTask（每日 03:00）或管理后台手动触发 → ItemCFEngine 计算 → 写 Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements IRecommendService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IProductService productService;
    private final TProductMapper productMapper;
    private final UserBehaviorLogMapper behaviorLogMapper;

    /** Redis Key 前缀（PRD §4.3.4） */
    private static final String KEY_USER_REC  = "rec:user:";
    private static final String KEY_ITEM_REC  = "rec:item:";
    /** 缓存 TTL 24 小时（离线任务写入） */
    private static final Duration TTL = Duration.ofHours(24);
    /** 实时算完写回的短 TTL，过期重算以纳入最新行为 */
    private static final Duration TTL_ONLINE = Duration.ofHours(1);

    // ====== F-R-005 首页"为你推荐" ======
    @Override
    public List<Long> personalRecommend(Long userId, int limit) {
        if (userId == null) {
            log.info("[Recommend] uid=null → coldStart (游客)");
            return coldStart(limit);
        }

        // 1. Redis 命中：直接返回（凌晨 3 点或手动重算写好的缓存）
        List<Long> cached = readList(KEY_USER_REC + userId);
        if (cached != null && !cached.isEmpty()) {
            log.info("[Recommend] uid={} → Redis 命中, ids={}", userId, cached);
            return cached.stream().limit(limit).toList();
        }

        // 2. Redis miss → 实时算（拉该用户近 30 天行为 + 全局 ItemCF）
        //    解决"重算推荐从未跑过 / 缓存为空"时所有用户都走 coldStart 的退化问题
        List<Long> onTheFly = computePersonalOnTheFly(userId, limit);
        if (!onTheFly.isEmpty()) {
            log.info("[Recommend] uid={} → Redis miss, 实时 ItemCF, ids={}", userId, onTheFly);
            return onTheFly;
        }

        // 3. ItemCF 算不出（数据稀疏 / 无共现）→ 基于"最近浏览分类的热销"做降级
        //    按用户对该分类的行为频次排序（偏好的分类先出），每个分类内按评分取 TOP，
        //    排除用户已交互过的。不同用户的偏好分类不同 → 推荐列表就不同。
        List<Long> categoryBased = recommendByRecentCategories(userId, limit);
        if (!categoryBased.isEmpty()) {
            log.info("[Recommend] uid={} → ItemCF 空, 分类偏好降级, ids={}", userId, categoryBased);
            return categoryBased;
        }

        // 4. 该用户完全没行为数据 → 真正的冷启动兜底
        log.info("[Recommend] uid={} → 无任何数据, 走 coldStart", userId);
        return coldStart(limit);
    }

    /**
     * 实时算个性化推荐：拉近 30 天所有用户的行为，构建物品相似度矩阵，针对当前用户召回。
     * 单次调用仅在该用户 Redis 缓存 miss 时触发（demo 数据量下几十 ms，不写 Redis）。
     */
    private List<Long> computePersonalOnTheFly(Long userId, int limit) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            List<UserBehaviorLog> logs = behaviorLogMapper.selectList(
                    new LambdaQueryWrapper<UserBehaviorLog>()
                            .ge(UserBehaviorLog::getOccurredAt, cutoff)
                            .isNotNull(UserBehaviorLog::getUserId)
                            .and(BehaviorScopes.realBehaviorOnly()));
            log.info("[Recommend] on-the-fly uid={} cutoff={} logs.size={}",
                    userId, cutoff, logs.size());
            if (logs.isEmpty()) return Collections.emptyList();

            ItemCFEngine engine = new ItemCFEngine();
            Map<Long, Map<Long, Double>> matrix = engine.buildUserItemMatrix(logs);
            log.info("[Recommend] on-the-fly uid={} matrix.users={} hasUid={}",
                    userId, matrix.size(), matrix.containsKey(userId));
            if (!matrix.containsKey(userId)) return Collections.emptyList(); // 该用户没行为

            Map<Long, List<SimilarItem>> itemSim = engine.computeItemSimilarity(matrix);
            Map<Long, List<Long>> userRecs = engine.generateUserRecommendations(matrix, itemSim);
            List<Long> result = userRecs.getOrDefault(userId, Collections.emptyList());
            // 实时算完写回 Redis（短 TTL），下次命中；过期重算可纳入最新行为
            if (!result.isEmpty()) {
                redisTemplate.opsForValue().set(KEY_USER_REC + userId, result, TTL_ONLINE);
            }
            log.info("[Recommend] on-the-fly uid={} result.size={} ids={}",
                    userId, result.size(), result);
            return result.stream().limit(limit).toList();
        } catch (Exception ex) {
            log.warn("[Recommend] on-the-fly compute failed uid={} err={}", userId, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * ItemCF 数据稀疏时的降级策略：基于用户最近浏览/加购过的商品所在的分类，
     * 按用户对该分类的偏好强度排序输出（偏好强的分类先出），
     * 每个分类内部按评分取 TOP，排除用户已交互过的。
     * 保证不同用户（浏览分类偏好不同）看到不同的推荐列表。
     */
    private List<Long> recommendByRecentCategories(Long userId, int limit) {
        try {
            // 1. 拉该用户最近 30 天内浏览/加购/收藏/下单的商品 id（最多 20 条最近的）
            List<UserBehaviorLog> recent = behaviorLogMapper.selectList(
                    new LambdaQueryWrapper<UserBehaviorLog>()
                            .eq(UserBehaviorLog::getUserId, userId)
                            .ge(UserBehaviorLog::getOccurredAt, LocalDateTime.now().minusDays(30))
                            .and(BehaviorScopes.realBehaviorOnly()) // 排除曝光垃圾，分类偏好才准
                            .orderByDesc(UserBehaviorLog::getId)
                            .last("LIMIT 20"));
            if (recent.isEmpty()) return Collections.emptyList();

            Set<Long> interactedIds = new HashSet<>();
            recent.forEach(l -> interactedIds.add(l.getProductId()));

            // 2. 统计每个分类的行为频次（用于排序偏好强弱）
            Map<Long, Integer> categoryFreq = new LinkedHashMap<>();
            for (UserBehaviorLog l : recent) {
                TProduct p = productService.getDetail(l.getProductId());
                if (p != null && p.getCategoryId() != null) {
                    categoryFreq.merge(p.getCategoryId(), 1, Integer::sum);
                }
            }
            if (categoryFreq.isEmpty()) return Collections.emptyList();

            // 3. 按频次降序排序分类（用户最偏好的在前）
            List<Map.Entry<Long, Integer>> sortedCats = new ArrayList<>(categoryFreq.entrySet());
            sortedCats.sort((a, b) -> b.getValue() - a.getValue());

            // 4. 每个偏好分类内部按评分取 TOP，按分类顺序依次填满 limit
            List<Long> result = new ArrayList<>();
            for (Map.Entry<Long, Integer> e : sortedCats) {
                if (result.size() >= limit) break;
                List<TProduct> topInCat = productService.list(e.getKey(), null, "rating-desc");
                for (TProduct p : topInCat) {
                    if (result.size() >= limit) break;
                    if (interactedIds.contains(p.getId())) continue;
                    if (result.contains(p.getId())) continue;
                    result.add(p.getId());
                }
            }
            log.info("[Recommend] category-fallback uid={} catFreq={} result.size={} ids={}",
                    userId, categoryFreq, result.size(), result);
            return result;
        } catch (Exception ex) {
            log.warn("[Recommend] category-based fallback failed uid={} err={}", userId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    // ====== F-R-006 详情页"猜你喜欢" ======
    @Override
    public List<Long> similarRecommend(Long productId, int limit) {
        if (productId == null) return coldStart(limit);
        List<Long> cached = readList(KEY_ITEM_REC + productId);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().limit(limit).toList();
        }
        // 该商品没有相似度数据 → 兜底：同分类销量 TOP
        try {
            TProduct p = productService.getDetail(productId);
            List<TProduct> sameCat = productService.list(p.getCategoryId(), null, "sales-desc");
            sameCat.removeIf(x -> x.getId().equals(productId));
            return sameCat.stream().limit(limit).map(TProduct::getId).toList();
        } catch (Exception ex) {
            return coldStart(limit);
        }
    }

    // ====== F-R-004 冷启动 ======
    @Override
    public List<Long> coldStart(int limit) {
        // 热门 TOP（按评分 + 销量综合）
        List<TProduct> top = productService.list(null, null, "rating-desc");
        return top.stream().limit(limit).map(TProduct::getId).toList();
    }

    @Override
    public boolean hasCachedPersonalRec(Long userId) {
        if (userId == null) return false;
        List<Long> cached = readList(KEY_USER_REC + userId);
        return cached != null && !cached.isEmpty();
    }

    // ====== 离线重算入口在 RecommendTask.rebuildAll() 里，
    //       由 RecommendController /admin/refresh 直接调用，不需要走 service 层 ======

    /** 读取 Redis List，类型擦除为 List<Long> */
    @SuppressWarnings("unchecked")
    private List<Long> readList(String key) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw instanceof List<?> list) {
                return list.stream()
                        .filter(o -> o instanceof Number)
                        .map(o -> ((Number) o).longValue())
                        .toList();
            }
        } catch (Exception ex) {
            log.warn("[Recommend] Redis read failed key={} err={}", key, ex.getMessage());
        }
        return null;
    }

    /** 写 Redis（被 RecommendTask 调用） */
    public void cacheUserRecommendations(Map<Long, List<Long>> userRecs) {
        userRecs.forEach((uid, recs) -> {
            try {
                redisTemplate.opsForValue().set(KEY_USER_REC + uid, recs, TTL);
            } catch (Exception ex) {
                log.warn("[Recommend] cache user {} failed: {}", uid, ex.getMessage());
            }
        });
    }

    public void cacheItemSimilarity(Map<Long, List<SimilarItem>> itemSim) {
        itemSim.forEach((iid, sims) -> {
            try {
                List<Long> ids = sims.stream().map(ItemCFEngine.SimilarItem::itemId).toList();
                redisTemplate.opsForValue().set(KEY_ITEM_REC + iid, ids, TTL);
            } catch (Exception ex) {
                log.warn("[Recommend] cache item {} failed: {}", iid, ex.getMessage());
            }
        });
    }
}