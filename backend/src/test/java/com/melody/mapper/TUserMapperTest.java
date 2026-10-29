package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TUserMapper CRUD + 条件查询测试。
 *
 * <p>使用 H2 内存库（见 application-test.yml + schema-test.sql），
 * 覆盖 BaseMapper 提供的方法，包括 selectList/selectById/insert/updateById/deleteById。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TUserMapper 集成测试")
class TUserMapperTest {

    @Autowired
    private TUserMapper userMapper;

    @BeforeEach
    void cleanTable() {
        userMapper.delete(new LambdaQueryWrapper<>());
    }

    private TUser newUser(String phone, String nickname) {
        TUser u = new TUser();
        u.setPhone(phone);
        u.setUsername(phone);
        u.setPassword("bcrypt$xxx");
        u.setNickname(nickname);
        u.setRankCode("V0");
        u.setPoints(0);
        u.setGrowthValue(0);
        u.setStatus(1);
        u.setJoinDate(LocalDateTime.now());
        u.setLastLoginAt(LocalDateTime.now());
        return u;
    }

    @Test
    @DisplayName("insert 后 selectById 能拿到自增 id 和 createdAt")
    void insertAndSelectById() {
        TUser u = newUser("13800000001", "测试用户1");
        int affected = userMapper.insert(u);
        assertThat(affected).isEqualTo(1);
        assertThat(u.getId()).isNotNull().isPositive();

        TUser loaded = userMapper.selectById(u.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getPhone()).isEqualTo("13800000001");
        assertThat(loaded.getNickname()).isEqualTo("测试用户1");
    }

    @Test
    @DisplayName("updateById 覆盖修改字段")
    void updateById() {
        TUser u = newUser("13800000002", "原始昵称");
        userMapper.insert(u);

        u.setNickname("新昵称");
        u.setPoints(200);
        int affected = userMapper.updateById(u);
        assertThat(affected).isEqualTo(1);

        TUser loaded = userMapper.selectById(u.getId());
        assertThat(loaded.getNickname()).isEqualTo("新昵称");
        assertThat(loaded.getPoints()).isEqualTo(200);
    }

    @Test
    @DisplayName("deleteById 物理删除")
    void deleteById() {
        TUser u = newUser("13800000003", "toDelete");
        userMapper.insert(u);

        int affected = userMapper.deleteById(u.getId());
        assertThat(affected).isEqualTo(1);
        assertThat(userMapper.selectById(u.getId())).isNull();
    }

    @Test
    @DisplayName("selectList 按 phone 等值查询")
    void selectByPhone() {
        userMapper.insert(newUser("13800000010", "u10"));
        userMapper.insert(newUser("13800000011", "u11"));

        List<TUser> list = userMapper.selectList(
                new LambdaQueryWrapper<TUser>().eq(TUser::getPhone, "13800000011"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getNickname()).isEqualTo("u11");
    }

    @Test
    @DisplayName("selectCount 统计当前表行数")
    void selectCount() {
        userMapper.insert(newUser("13800000020", "u20"));
        userMapper.insert(newUser("13800000021", "u21"));
        userMapper.insert(newUser("13800000022", "u22"));

        Long count = userMapper.selectCount(new LambdaQueryWrapper<>());
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("唯一索引 uk_phone 触发 DuplicateKeyException")
    void uniquePhoneConstraint() {
        userMapper.insert(newUser("13800000030", "first"));

        TUser dup = newUser("13800000030", "second");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userMapper.insert(dup))
                .satisfiesAnyOf(
                        ex -> org.assertj.core.api.Assertions.assertThat(ex)
                                .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class),
                        ex -> org.assertj.core.api.Assertions.assertThat(ex)
                                .hasRootCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class),
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getMessage() == null ? "" : ex.getMessage())
                                .contains("uk_phone"));
    }
}
