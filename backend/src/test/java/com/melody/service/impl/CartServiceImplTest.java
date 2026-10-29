package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.ResultCode;
import com.melody.dto.CheckoutDTOs.CartItemVO;
import com.melody.entity.TCartItem;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.exception.BizException;
import com.melody.mapper.TCartItemMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductSkuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CartServiceImpl 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartServiceImpl 单元测试")
class CartServiceImplTest {

    @Mock private TCartItemMapper cartMapper;
    @Mock private TProductSkuMapper skuMapper;
    @Mock private TProductMapper productMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    private TCartItem item(Long id, Long userId, Long productId, Long skuId, int qty, Integer selected) {
        TCartItem i = new TCartItem();
        i.setId(id);
        i.setUserId(userId);
        i.setProductId(productId);
        i.setSkuId(skuId);
        i.setQuantity(qty);
        i.setPrice(new BigDecimal("9.90"));
        i.setSpecName("默认规格");
        i.setSelected(selected);
        return i;
    }

    // ============ list ============

    @Test
    @DisplayName("list 空购物车 → 空列表")
    void list_empty() {
        when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<CartItemVO> result = cartService.list(1L);
        assertThat(result).isEmpty();
        verify(productMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("list 回填商品名和封面图")
    void list_fillsProductInfo() {
        TCartItem i = item(1L, 1L, 100L, 10L, 2, 1);
        when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(i));

        TProduct p = new TProduct();
        p.setId(100L);
        p.setName("可乐");
        p.setCoverImage("/img/coke.jpg");
        when(productMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(p));

        List<CartItemVO> result = cartService.list(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("可乐");
        assertThat(result.get(0).getProductImage()).isEqualTo("/img/coke.jpg");
        assertThat(result.get(0).getQuantity()).isEqualTo(2);
    }

    // ============ add ============

    @Test
    @DisplayName("add 全新商品 → 新建记录，默认 selected=1")
    void add_newItem() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TCartItem result = cartService.add(1L, 100L, 10L, 2, new BigDecimal("9.90"), "大份");

        assertThat(result.getSelected()).isEqualTo(1);
        verify(cartMapper).insert(any(TCartItem.class));
    }

    @Test
    @DisplayName("add 已存在商品 → 数量累加而非新建")
    void add_existingItem_accumulatesQuantity() {
        TCartItem exist = item(1L, 1L, 100L, 10L, 2, 1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        // 传具体 price 避免触发 sku 回填逻辑
        TCartItem result = cartService.add(1L, 100L, 10L, 3, new BigDecimal("9.90"), "默认规格");

        assertThat(result.getQuantity()).isEqualTo(5); // 2 + 3
        verify(cartMapper, never()).insert(any(TCartItem.class));
        verify(cartMapper, times(1)).updateById(exist);
    }

    @Test
    @DisplayName("add 数量 0 / null → BAD_REQUEST")
    void add_invalidQuantity() {
        assertThatThrownBy(() -> cartService.add(1L, 100L, 10L, 0, null, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("add price/specName 缺失时按 sku 自动补")
    void add_fillsFromSku() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TProductSku sku = new TProductSku();
        sku.setId(10L);
        sku.setPrice(new BigDecimal("12.00"));
        sku.setSpecName("中份");
        when(skuMapper.selectById(10L)).thenReturn(sku);

        TCartItem result = cartService.add(1L, 100L, 10L, 1, null, null);

        assertThat(result.getPrice()).isEqualByComparingTo("12.00");
        assertThat(result.getSpecName()).isEqualTo("中份");
    }

    @Test
    @DisplayName("add price 仍缺失且 sku 也没了 → BAD_REQUEST")
    void add_priceStillMissing() {
        // sku 也不存在 → price 仍 null → 抛异常（cartMapper 在 price==null 校验之前不会被调用）
        org.mockito.Mockito.lenient().when(skuMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> cartService.add(1L, 100L, 10L, 1, null, null))
                .isInstanceOf(BizException.class);
    }

    // ============ updateQuantity ============

    @Test
    @DisplayName("updateQuantity 正数 → 修改数量")
    void updateQuantity_positive() {
        TCartItem exist = item(1L, 1L, 100L, 10L, 1, 1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        cartService.updateQuantity(1L, 100L, 10L, 5);

        assertThat(exist.getQuantity()).isEqualTo(5);
        verify(cartMapper).updateById(exist);
        verify(cartMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("updateQuantity 0/负数 → 删除记录")
    void updateQuantity_zeroOrNegative_deletes() {
        TCartItem exist = item(1L, 1L, 100L, 10L, 1, 1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        cartService.updateQuantity(1L, 100L, 10L, 0);

        verify(cartMapper).deleteById(1L);
    }

    @Test
    @DisplayName("updateQuantity 找不到记录 → NOT_FOUND")
    void updateQuantity_notFound() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> cartService.updateQuantity(1L, 100L, 10L, 3))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    // ============ toggleSelect / setSelect / toggleSelectAll / clearSelected ============

    @Test
    @DisplayName("toggleSelect 当前 0 → 切到 1")
    void toggleSelect_zeroToOne() {
        TCartItem exist = item(1L, 1L, 100L, 10L, 1, 0);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        cartService.toggleSelect(1L, 100L, 10L);

        assertThat(exist.getSelected()).isEqualTo(1);
        verify(cartMapper).updateById(exist);
    }

    @Test
    @DisplayName("toggleSelect 当前 1 → 切到 0")
    void toggleSelect_oneToZero() {
        TCartItem exist = item(1L, 1L, 100L, 10L, 1, 1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        cartService.toggleSelect(1L, 100L, 10L);

        assertThat(exist.getSelected()).isEqualTo(0);
    }

    @Test
    @DisplayName("toggleSelect 找不到 → 静默 no-op")
    void toggleSelect_notFound_noop() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        cartService.toggleSelect(1L, 100L, 10L);

        verify(cartMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("toggleSelectAll 全选")
    void toggleSelectAll_selectAll() {
        TCartItem a = item(1L, 1L, 100L, 10L, 1, 0);
        TCartItem b = item(2L, 1L, 101L, 11L, 1, 0);
        when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a, b));

        cartService.toggleSelectAll(1L, true);

        assertThat(a.getSelected()).isEqualTo(1);
        assertThat(b.getSelected()).isEqualTo(1);
        verify(cartMapper, times(2)).updateById(any(TCartItem.class));
    }

    @Test
    @DisplayName("clearSelected 仅删 selected=1")
    void clearSelected_deletesOnlySelected() {
        TCartItem a = item(1L, 1L, 100L, 10L, 1, 1);
        TCartItem b = item(2L, 1L, 101L, 11L, 1, 0);
        when(cartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a, b));

        cartService.clearSelected(1L);

        verify(cartMapper).deleteById(1L);
        verify(cartMapper, never()).deleteById(2L);
    }
}
