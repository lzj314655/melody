package com.melody.service.recommend;

import com.melody.entity.UserBehaviorLog;
import com.melody.service.recommend.ItemCFEngine.SimilarItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ItemCFEngine 纯算法测试 — 不依赖 Spring / MyBatis / Redis。
 *
 * <p>覆盖三大方法：
 * <ul>
 *   <li>{@link ItemCFEngine#buildUserItemMatrix}</li>
 *   <li>{@link ItemCFEngine#computeItemSimilarity}</li>
 *   <li>{@link ItemCFEngine#generateUserRecommendations}</li>
 * </ul>
 * </p>
 */
@DisplayName("ItemCFEngine 算法测试")
class ItemCFEngineTest {

    private ItemCFEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ItemCFEngine();
    }

    private UserBehaviorLog log(Long uid, Long pid, String type) {
        UserBehaviorLog l = new UserBehaviorLog();
        l.setUserId(uid);
        l.setProductId(pid);
        l.setBehaviorType(type);
        return l;
    }

    // ============ buildUserItemMatrix ============

    @Test
    @DisplayName("空列表 → 空矩阵")
    void buildMatrix_empty() {
        Map<Long, Map<Long, Double>> m = engine.buildUserItemMatrix(Collections.emptyList());
        assertThat(m).isEmpty();
    }

    @Test
    @DisplayName("同一 (user, item) 多次同类型行为权重累加")
    void buildMatrix_accumulateSameType() {
        // u1 对 p1 VIEW 了 3 次 → 1+1+1 = 3
        List<UserBehaviorLog> logs = List.of(
                log(1L, 100L, "VIEW"),
                log(1L, 100L, "VIEW"),
                log(1L, 100L, "VIEW")
        );

        Map<Long, Map<Long, Double>> m = engine.buildUserItemMatrix(logs);
        assertThat(m.get(1L).get(100L)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("不同用户各自维护独立的物品向量")
    void buildMatrix_isolateUsers() {
        List<UserBehaviorLog> logs = List.of(
                log(1L, 100L, "VIEW"),
                log(2L, 200L, "VIEW")
        );

        Map<Long, Map<Long, Double>> m = engine.buildUserItemMatrix(logs);
        assertThat(m).containsOnlyKeys(1L, 2L);
        assertThat(m.get(1L)).containsOnlyKeys(100L);
        assertThat(m.get(2L)).containsOnlyKeys(200L);
    }

    @Test
    @DisplayName("不同行为类型权重：VIEW=1 / ADD_CART=3 / FAVORITE=4 / ORDER=5")
    void buildMatrix_weightsByType() {
        List<UserBehaviorLog> logs = List.of(
                log(1L, 100L, "VIEW"),
                log(1L, 101L, "ADD_CART"),
                log(1L, 102L, "FAVORITE"),
                log(1L, 103L, "ORDER")
        );

        Map<Long, Map<Long, Double>> m = engine.buildUserItemMatrix(logs);
        assertThat(m.get(1L).get(100L)).isEqualTo(1.0);
        assertThat(m.get(1L).get(101L)).isEqualTo(3.0);
        assertThat(m.get(1L).get(102L)).isEqualTo(4.0);
        assertThat(m.get(1L).get(103L)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("未知行为类型被跳过")
    void buildMatrix_unknownTypeSkipped() {
        List<UserBehaviorLog> logs = List.of(
                log(1L, 100L, "VIEW"),
                log(1L, 101L, "UNKNOWN_TYPE")  // 不在 WEIGHT_MAP → 跳过
        );

        Map<Long, Map<Long, Double>> m = engine.buildUserItemMatrix(logs);
        assertThat(m.get(1L)).containsOnlyKeys(100L);
    }

    @Test
    @DisplayName("游客（userId=null）或无 productId 的记录被跳过")
    void buildMatrix_skipGuestOrNoProduct() {
        List<UserBehaviorLog> logs = new ArrayList<>();
        UserBehaviorLog guest = new UserBehaviorLog();
        guest.setUserId(null);
        guest.setProductId(100L);
        guest.setBehaviorType("VIEW");
        logs.add(guest);

        UserBehaviorLog noProduct = new UserBehaviorLog();
        noProduct.setUserId(1L);
        noProduct.setProductId(null);
        noProduct.setBehaviorType("VIEW");
        logs.add(noProduct);

        Map<Long, Map<Long, Double>> m = engine.buildUserItemMatrix(logs);
        assertThat(m).isEmpty();
    }

    // ============ computeItemSimilarity ============

    @Test
    @DisplayName("完全共现的两个物品相似度为 1.0")
    void similarity_perfectCoincidence() {
        // u1 u2 都对 p1 p2 行为 → 相似度 = 1
        Map<Long, Map<Long, Double>> m = Map.of(
                1L, Map.of(100L, 1.0, 200L, 1.0),
                2L, Map.of(100L, 1.0, 200L, 1.0)
        );

        Map<Long, List<SimilarItem>> sim = engine.computeItemSimilarity(m);
        assertThat(sim.get(100L)).hasSize(1);
        assertThat(sim.get(100L).get(0).itemId()).isEqualTo(200L);
        assertThat(sim.get(100L).get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("无共现物品不进入相似列表")
    void similarity_noCoincidenceExcluded() {
        // u1 只交互 p1，u2 只交互 p2 → 没有共同用户
        Map<Long, Map<Long, Double>> m = Map.of(
                1L, Map.of(100L, 1.0),
                2L, Map.of(200L, 1.0)
        );

        Map<Long, List<SimilarItem>> sim = engine.computeItemSimilarity(m);
        assertThat(sim.get(100L)).isEmpty();
        assertThat(sim.get(200L)).isEmpty();
    }

    @Test
    @DisplayName("单个物品 → 相似列表为空")
    void similarity_singleItem() {
        Map<Long, Map<Long, Double>> m = Map.of(1L, Map.of(100L, 1.0));
        Map<Long, List<SimilarItem>> sim = engine.computeItemSimilarity(m);
        assertThat(sim.get(100L)).isEmpty();
    }

    @Test
    @DisplayName("相似物品按相似度降序排列")
    void similarity_descendingOrder() {
        // 100 和 200 都用户 {u1, u2, u3} 行为
        // 100 和 300 只 {u1, u2} 行为（更稀疏）→ 相似度更低
        Map<Long, Map<Long, Double>> m = Map.of(
                1L, Map.of(100L, 1.0, 200L, 1.0, 300L, 1.0),
                2L, Map.of(100L, 1.0, 200L, 1.0, 300L, 1.0),
                3L, Map.of(100L, 1.0, 200L, 1.0)
        );

        Map<Long, List<SimilarItem>> sim = engine.computeItemSimilarity(m);
        List<SimilarItem> topFor100 = sim.get(100L);
        assertThat(topFor100).hasSize(2);
        assertThat(topFor100.get(0).itemId()).isEqualTo(200L); // 更高相似度
        assertThat(topFor100.get(1).itemId()).isEqualTo(300L);
        assertThat(topFor100.get(0).score()).isGreaterThan(topFor100.get(1).score());
    }

    // ============ generateUserRecommendations ============

    @Test
    @DisplayName("排除用户已交互物品")
    void recommend_excludeInteracted() {
        // 用户矩阵：u1 交互过 {100, 200}；物品相似：100→{200, 300}
        Map<Long, Map<Long, Double>> userItem = Map.of(1L, Map.of(100L, 1.0, 200L, 1.0));
        Map<Long, List<SimilarItem>> itemSim = Map.of(
                100L, List.of(new SimilarItem(200L, 0.9), new SimilarItem(300L, 0.5))
        );

        Map<Long, List<Long>> recs = engine.generateUserRecommendations(userItem, itemSim);
        // 200 已交互，排除；只剩 300
        assertThat(recs.get(1L)).containsExactly(300L);
    }

    @Test
    @DisplayName("无相似物品 → 推荐为空")
    void recommend_noSimilarItems() {
        Map<Long, Map<Long, Double>> userItem = Map.of(1L, Map.of(100L, 1.0));
        Map<Long, List<SimilarItem>> itemSim = Collections.emptyMap();

        Map<Long, List<Long>> recs = engine.generateUserRecommendations(userItem, itemSim);
        assertThat(recs.get(1L)).isEmpty();
    }

    @Test
    @DisplayName("相似度分数累加 → 多物品来源排序")
    void recommend_aggregateScores() {
        // u1 交互 {100, 200}
        // 100 的相似 → {300=0.8}
        // 200 的相似 → {300=0.5, 400=0.6}
        // u1 的推荐：300 累加 0.8+0.5=1.3, 400=0.6 → 300 在前
        Map<Long, Map<Long, Double>> userItem = Map.of(1L, Map.of(100L, 1.0, 200L, 1.0));
        Map<Long, List<SimilarItem>> itemSim = Map.of(
                100L, List.of(new SimilarItem(300L, 0.8)),
                200L, List.of(new SimilarItem(300L, 0.5), new SimilarItem(400L, 0.6))
        );

        Map<Long, List<Long>> recs = engine.generateUserRecommendations(userItem, itemSim);
        assertThat(recs.get(1L)).containsExactly(300L, 400L);
    }

    @Test
    @DisplayName("推荐结果截断到 TOP_N_USER(20)")
    void recommend_truncateToTopN() {
        // 造 25 个候选相似物品
        Map<Long, Double> u1 = new java.util.HashMap<>();
        u1.put(100L, 1.0);
        // 100 的相似 → 25 个物品，分数各异
        List<SimilarItem> sims = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            sims.add(new SimilarItem((long) (200 + i), 0.9 - i * 0.01));
        }

        Map<Long, List<Long>> recs = engine.generateUserRecommendations(
                Map.of(1L, u1),
                Map.of(100L, sims));
        assertThat(recs.get(1L)).hasSize(ItemCFEngine.TOP_N_USER);
    }

    @Test
    @DisplayName("多用户各自独立生成推荐")
    void recommend_isolateUsers() {
        Map<Long, Map<Long, Double>> userItem = Map.of(
                1L, Map.of(100L, 1.0),
                2L, Map.of(200L, 1.0)
        );
        Map<Long, List<SimilarItem>> itemSim = Map.of(
                100L, List.of(new SimilarItem(300L, 0.9)),
                200L, List.of(new SimilarItem(400L, 0.8))
        );

        Map<Long, List<Long>> recs = engine.generateUserRecommendations(userItem, itemSim);
        assertThat(recs.get(1L)).containsExactly(300L);
        assertThat(recs.get(2L)).containsExactly(400L);
    }
}
