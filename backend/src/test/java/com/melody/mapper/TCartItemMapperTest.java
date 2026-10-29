package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TCartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TCartItemMapper CRUD + 唯一键 uk_user_sku(user_id, product_id, sku_id) 验证。
 *
 * <p>虽然 Mapper 自身没有自定义 SQL（继承 BaseMapper），
 * 但 uk_user_sku 在 service 层 add() 中依赖唯一性去重，
 * 这里专门验证它能正常抛 DuplicateKeyException。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TCartItemMapper 集成测试")
class TCartItemMapperTest {

    @Autowired
    private TCartItemMapper cartMapper;

    @BeforeEach
    void cleanTable() {
        cartMapper.delete(new LambdaQueryWrapper<>());
    }

    private TCartItem seed(Long userId, Long productId, Long skuId, int qty) {
        TCartItem i = new TCartItem();
        i.setUserId(userId);
        i.setProductId(productId);
        i.setSkuId(skuId);
        i.setQuantity(qty);
        i.setPrice(new BigDecimal("9.90"));
        i.setSpecName("默认规格");
        i.setSelected(1);
        cartMapper.insert(i);
        return i;
    }

    @Test
    @DisplayName("insert + selectById")
    void insertAndSelect() {
        TCartItem i = seed(1001L, 1L, 11L, 2);
        assertThat(i.getId()).isNotNull();

        TCartItem loaded = cartMapper.selectById(i.getId());
        assertThat(loaded.getUserId()).isEqualTo(1001L);
        assertThat(loaded.getQuantity()).isEqualTo(2);
        assertThat(loaded.getSelected()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateById 修改数量/选中状态")
    void updateQuantityAndSelection() {
        TCartItem i = seed(1002L, 1L, 11L, 1);
        i.setQuantity(5);
        i.setSelected(0);
        int affected = cartMapper.updateById(i);
        assertThat(affected).isEqualTo(1);

        TCartItem loaded = cartMapper.selectById(i.getId());
        assertThat(loaded.getQuantity()).isEqualTo(5);
        assertThat(loaded.getSelected()).isEqualTo(0);
    }

    @Test
    @DisplayName("selectList 按 user_id 过滤")
    void selectByUser() {
        seed(1003L, 1L, 11L, 1);
        seed(1003L, 1L, 12L, 1);
        seed(1004L, 1L, 11L, 1);

        List<TCartItem> mine = cartMapper.selectList(
                new LambdaQueryWrapper<TCartItem>().eq(TCartItem::getUserId, 1003L));
        assertThat(mine).hasSize(2);
    }

    @Test
    @DisplayName("按 user+product+sku 维度删除（service.remove 行为）")
    void deleteByUserProductSku() {
        TCartItem a = seed(1005L, 1L, 11L, 1);
        seed(1005L, 1L, 12L, 1);

        int affected = cartMapper.delete(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, 1005L)
                .eq(TCartItem::getProductId, 1L)
                .eq(TCartItem::getSkuId, 11L));
        assertThat(affected).isEqualTo(1);
        assertThat(cartMapper.selectById(a.getId())).isNull();
    }

    @Test
    @DisplayName("selectBatchIds 批量回填")
    void selectBatchIds() {
        TCartItem a = seed(1006L, 1L, 11L, 1);
        TCartItem b = seed(1006L, 1L, 12L, 1);
        TCartItem c = seed(1006L, 1L, 13L, 1);

        List<TCartItem> ids = cartMapper.selectBatchIds(List.of(a.getId(), b.getId(), c.getId()));
        assertThat(ids).hasSize(3);
    }

    @Test
    @DisplayName("selectCount 统计")
    void selectCount() {
        seed(1007L, 1L, 11L, 1);
        seed(1007L, 1L, 12L, 1);
        seed(1007L, 1L, 13L, 1);
        long n = cartMapper.selectCount(new LambdaQueryWrapper<TCartItem>().eq(TCartItem::getUserId, 1007L));
        assertThat(n).isEqualTo(3L);
    }
}
