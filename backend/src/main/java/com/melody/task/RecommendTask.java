package com.melody.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.UserBehaviorLog;
import com.melody.mapper.UserBehaviorLogMapper;
import com.melody.service.impl.RecommendServiceImpl;
import com.melody.service.recommend.BehaviorScopes;
import com.melody.service.recommend.ItemCFEngine;
import com.melody.service.recommend.ItemCFEngine.SimilarItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 推荐系统离线计算任务
 *
 * PRD §4.3.4：每日凌晨 3 点重新计算相似度矩阵，保证推荐新鲜度与计算成本平衡
 *
 * 步骤：
 *   1. 拉取近 30 天的 user_behavior_log
 *   2. ItemCFEngine 构建用户-物品矩阵
 *   3. 计算物品-物品 Top-20 相似度
 *   4. 生成用户-物品 Top-20 推荐
 *   5. 写入 Redis（TTL 24h）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendTask {

    private final UserBehaviorLogMapper behaviorLogMapper;
    private final ItemCFEngine engine = new ItemCFEngine();
    private final RecommendServiceImpl recommendService;

    /** 每日 03:00 触发（PRD §4.3.4） */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyRebuild() {
        log.info("[RecommendTask] 每日重算开始 {}", LocalDateTime.now());
        long t0 = System.currentTimeMillis();
        try {
            int affected = rebuildAll();
            log.info("[RecommendTask] 每日重算完成, 影响用户={} 条, 耗时={}ms",
                    affected, System.currentTimeMillis() - t0);
        } catch (Exception ex) {
            log.error("[RecommendTask] 重算失败", ex);
        }
    }

    /** 手动触发（管理后台 / 启动时预热） */
    public int rebuildAll() {
        // 1. 拉近 30 天行为
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<UserBehaviorLog> logs = behaviorLogMapper.selectList(
                new LambdaQueryWrapper<UserBehaviorLog>()
                        .ge(UserBehaviorLog::getOccurredAt, cutoff)
                        .isNotNull(UserBehaviorLog::getUserId)
                        .and(BehaviorScopes.realBehaviorOnly()) // 排除 list/test/uat 曝光垃圾
        );
        log.info("[RecommendTask] 拉取近 30 天行为 {} 条", logs.size());
        if (logs.isEmpty()) {
            return 0;
        }

        // 2. 步骤 1：用户-物品矩阵
        Map<Long, Map<Long, Double>> userItem = engine.buildUserItemMatrix(logs);

        // 3. 步骤 2：物品-物品相似度
        Map<Long, List<SimilarItem>> itemSim = engine.computeItemSimilarity(userItem);

        // 4. 步骤 3：用户 Top-N 推荐
        Map<Long, List<Long>> userRecs = engine.generateUserRecommendations(userItem, itemSim);

        // 5. 写 Redis
        recommendService.cacheItemSimilarity(itemSim);
        recommendService.cacheUserRecommendations(userRecs);

        log.info("[RecommendTask] 写入完成: 商品相似度 {} 个, 用户推荐 {} 个",
                itemSim.size(), userRecs.size());
        return userRecs.size();
    }
}