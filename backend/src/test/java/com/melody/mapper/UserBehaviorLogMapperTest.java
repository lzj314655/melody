package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.UserBehaviorLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserBehaviorLogMapper 测试：CRUD + 行为类型过滤。
 *
 * <p>RecommendServiceImpl 大量依赖此表做召回（ItemCF、分类降级）。
 * extra JSON 列在 schema-test.sql 中改为 TEXT。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UserBehaviorLogMapper 集成测试")
class UserBehaviorLogMapperTest {

    @Autowired
    private UserBehaviorLogMapper logMapper;

    @BeforeEach
    void cleanTable() {
        logMapper.delete(new LambdaQueryWrapper<>());
    }

    private UserBehaviorLog seed(Long userId, Long productId, String type, int weight, String ip) {
        UserBehaviorLog l = new UserBehaviorLog();
        l.setUserId(userId);
        l.setProductId(productId);
        l.setBehaviorType(type);
        l.setWeight(weight);
        l.setClientIp(ip);
        l.setExtra(new HashMap<>(Map.of("from", "home")));
        logMapper.insert(l);
        return l;
    }

    @Test
    @DisplayName("insert + selectById 保留字段")
    void insertAndSelect() {
        UserBehaviorLog l = seed(1L, 100L, "VIEW", 1, "127.0.0.1");
        assertThat(l.getId()).isNotNull();

        UserBehaviorLog loaded = logMapper.selectById(l.getId());
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getBehaviorType()).isEqualTo("VIEW");
        assertThat(loaded.getWeight()).isEqualTo(1);
        assertThat(loaded.getClientIp()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("按 user_id 过滤")
    void selectByUser() {
        seed(1L, 100L, "VIEW", 1, null);
        seed(1L, 101L, "ADD_CART", 3, null);
        seed(2L, 100L, "VIEW", 1, null);

        List<UserBehaviorLog> mine = logMapper.selectList(
                new LambdaQueryWrapper<UserBehaviorLog>().eq(UserBehaviorLog::getUserId, 1L));
        assertThat(mine).hasSize(2);
    }

    @Test
    @DisplayName("按 product_id 过滤")
    void selectByProduct() {
        seed(1L, 100L, "VIEW", 1, null);
        seed(2L, 100L, "FAVORITE", 4, null);
        seed(1L, 101L, "ORDER", 5, null);

        List<UserBehaviorLog> ofP100 = logMapper.selectList(
                new LambdaQueryWrapper<UserBehaviorLog>().eq(UserBehaviorLog::getProductId, 100L));
        assertThat(ofP100).hasSize(2);
    }

    @Test
    @DisplayName("按 behavior_type 过滤")
    void selectByType() {
        seed(1L, 100L, "VIEW", 1, null);
        seed(1L, 101L, "VIEW", 1, null);
        seed(1L, 102L, "ORDER", 5, null);

        long views = logMapper.selectCount(
                new LambdaQueryWrapper<UserBehaviorLog>().eq(UserBehaviorLog::getBehaviorType, "VIEW"));
        assertThat(views).isEqualTo(2L);

        long orders = logMapper.selectCount(
                new LambdaQueryWrapper<UserBehaviorLog>().eq(UserBehaviorLog::getBehaviorType, "ORDER"));
        assertThat(orders).isEqualTo(1L);
    }

    @Test
    @DisplayName("游客行为 userId=null — RecommendServiceImpl 跳过这类")
    void guestBehavior() {
        UserBehaviorLog guest = new UserBehaviorLog();
        guest.setUserId(null);
        guest.setProductId(100L);
        guest.setBehaviorType("VIEW");
        guest.setWeight(1);
        logMapper.insert(guest);

        List<UserBehaviorLog> guests = logMapper.selectList(
                new LambdaQueryWrapper<UserBehaviorLog>().isNull(UserBehaviorLog::getUserId));
        assertThat(guests).hasSize(1);
    }

    @Test
    @DisplayName("extra JSON 写入/读取")
    void extraJsonField() {
        UserBehaviorLog l = new UserBehaviorLog();
        l.setUserId(1L);
        l.setProductId(100L);
        l.setBehaviorType("VIEW");
        l.setWeight(1);
        Map<String, Object> extra = new HashMap<>();
        extra.put("spec", "大份");
        extra.put("qty", 3);
        l.setExtra(extra);
        logMapper.insert(l);

        UserBehaviorLog loaded = logMapper.selectById(l.getId());
        assertThat(loaded.getExtra()).isNotNull();
        assertThat(loaded.getExtra().get("spec")).isEqualTo("大份");
        assertThat(loaded.getExtra().get("qty")).isEqualTo(3);
    }
}
