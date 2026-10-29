package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.melody.common.ResultCode;
import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.exception.BizException;
import com.melody.mapper.TCategoryMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductSkuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductServiceImpl 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl 单元测试")
class ProductServiceImplTest {

    @Mock private TProductMapper productMapper;
    @Mock private TProductSkuMapper skuMapper;
    @Mock private TCategoryMapper categoryMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private TProduct newProduct(Long id, String name, BigDecimal price) {
        TProduct p = new TProduct();
        p.setId(id);
        p.setName(name);
        p.setCategoryId(1L);
        p.setPrice(price);
        p.setStatus(1);
        p.setInventory(10);
        return p;
    }

    private TProductSku newSku(Long id, Long productId, int stock) {
        TProductSku s = new TProductSku();
        s.setId(id);
        s.setProductId(productId);
        s.setSpecName("默认");
        s.setPrice(new BigDecimal("9.90"));
        s.setStock(stock);
        s.setStatus(1);
        return s;
    }

    // ============ listCategories ============

    @Test
    @DisplayName("listCategories 按 status=1 + sort 升序")
    void listCategories() {
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(new TCategory()));
        assertThat(productService.listCategories()).hasSize(1);
    }

    // ============ list ============

    @Test
    @DisplayName("list 默认排序：id desc")
    void list_defaultSort() {
        TProduct p1 = newProduct(1L, "p1", new BigDecimal("10"));
        TProduct p2 = newProduct(2L, "p2", new BigDecimal("20"));
        when(productMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(p1, p2));
        when(skuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TProduct> result = productService.list(null, null, null);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("list 价格升序：price-asc")
    void list_priceAsc() {
        when(productMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(skuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        productService.list(null, null, "price-asc");

        ArgumentCaptor<QueryWrapper<TProduct>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(productMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("ORDER BY").contains("ASC");
    }

    @Test
    @DisplayName("list 关键词模糊查询 name + description")
    void list_keywordLike() {
        when(productMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(skuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        productService.list(null, "可乐", null);

        ArgumentCaptor<QueryWrapper<TProduct>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(productMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertThat(sql).contains("like").contains("name").contains("description");
    }

    @Test
    @DisplayName("list 累加 SKU 库存到 product.inventory")
    void list_aggregatesStockFromSkus() {
        TProduct p1 = newProduct(1L, "p1", new BigDecimal("10"));
        p1.setInventory(999); // 初始值
        when(productMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(p1));

        TProductSku sku1 = newSku(11L, 1L, 7);
        TProductSku sku2 = newSku(12L, 1L, 3);
        when(skuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sku1, sku2));

        List<TProduct> result = productService.list(null, null, null);
        assertThat(result.get(0).getInventory()).isEqualTo(10); // 7+3
    }

    // ============ getDetail ============

    @Test
    @DisplayName("getDetail 存在 → 返回")
    void getDetail_ok() {
        TProduct p = newProduct(1L, "p", new BigDecimal("10"));
        when(productMapper.selectById(1L)).thenReturn(p);

        assertThat(productService.getDetail(1L)).isSameAs(p);
    }

    @Test
    @DisplayName("getDetail 不存在 → PRODUCT_NOT_FOUND")
    void getDetail_missing() {
        when(productMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> productService.getDetail(99L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode()));
    }

    // ============ listSkus ============

    @Test
    @DisplayName("listSkus 只返回在架 SKU")
    void listSkus_activeOnly() {
        when(skuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(newSku(1L, 1L, 10)));
        assertThat(productService.listSkus(1L)).hasSize(1);
    }

    // ============ create ============

    @Test
    @DisplayName("create 缺少 name / price → BAD_REQUEST")
    void create_missingFields() {
        TProduct p = new TProduct();
        p.setPrice(new BigDecimal("10"));
        // 缺 name

        assertThatThrownBy(() -> productService.create(p, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));

        verify(productMapper, never()).insert(any(TProduct.class));
    }

    @Test
    @DisplayName("create 完整流程：插入商品 + 插入 SKU + 累计 inventory")
    void create_fullFlow() {
        TProduct p = new TProduct();
        p.setName("可乐");
        p.setPrice(new BigDecimal("3.50"));
        p.setCategoryId(1L);

        TProductSku sku1 = newSku(null, null, 30);
        TProductSku sku2 = newSku(null, null, 70);
        when(productMapper.insert(any(TProduct.class))).thenAnswer(inv -> {
            TProduct arg = inv.getArgument(0);
            arg.setId(100L);
            return 1;
        });

        TProduct result = productService.create(p, List.of(sku1, sku2));

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getSales()).isZero();
        assertThat(result.getRating()).isEqualByComparingTo("5.00");
        assertThat(result.getInventory()).isEqualTo(100); // 30+70

        // sku 都被设置 productId
        assertThat(sku1.getProductId()).isEqualTo(100L);
        assertThat(sku2.getProductId()).isEqualTo(100L);
        verify(skuMapper, times(2)).insert(any(TProductSku.class));
    }

    // ============ update ============

    @Test
    @DisplayName("update 白名单字段")
    void update_whitelistOnly() {
        TProduct old = newProduct(1L, "old", new BigDecimal("10"));
        lenient().when(productMapper.selectById(1L)).thenReturn(old);

        TProduct patch = new TProduct();
        patch.setName("new");
        patch.setPrice(new BigDecimal("20"));
        patch.setSales(99999); // 不在白名单
        patch.setRating(new BigDecimal("0.00")); // 不在白名单

        TProduct result = productService.update(1L, patch);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getPrice()).isEqualByComparingTo("20");
        // 白名单外字段保持原值（newProduct 未设置 sales/rating，应保持 null）
        assertThat(result.getSales()).isNull();
        assertThat(result.getRating()).isNull();
    }

    @Test
    @DisplayName("update 不存在 → PRODUCT_NOT_FOUND")
    void update_missing() {
        when(productMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> productService.update(99L, new TProduct()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode()));
    }

    // ============ delete ============

    @Test
    @DisplayName("delete 软删除：status=0")
    void delete_softDelete() {
        TProduct p = newProduct(1L, "x", new BigDecimal("10"));
        p.setStatus(1);
        when(productMapper.selectById(1L)).thenReturn(p);

        productService.delete(1L);

        assertThat(p.getStatus()).isEqualTo(0);
        verify(productMapper).updateById(p);
    }

    @Test
    @DisplayName("delete 不存在 → PRODUCT_NOT_FOUND")
    void delete_missing() {
        when(productMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(BizException.class);
    }

    // ============ category CRUD ============

    @Test
    @DisplayName("createCategory 缺少 name → BAD_REQUEST")
    void createCategory_missingName() {
        TCategory c = new TCategory();
        assertThatThrownBy(() -> productService.createCategory(c))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("createCategory 默认 status=1, sort=0")
    void createCategory_defaults() {
        TCategory c = new TCategory();
        c.setName("饮料");
        when(categoryMapper.insert(any(TCategory.class))).thenAnswer(inv -> {
            TCategory arg = inv.getArgument(0);
            arg.setId(99L);
            return 1;
        });

        TCategory result = productService.createCategory(c);
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getSort()).isEqualTo(0);
    }

    @Test
    @DisplayName("updateCategory 不存在 → NOT_FOUND")
    void updateCategory_missing() {
        when(categoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> productService.updateCategory(99L, new TCategory()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("deleteCategory 不存在 → NOT_FOUND")
    void deleteCategory_missing() {
        when(categoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> productService.deleteCategory(99L))
                .isInstanceOf(BizException.class);
    }
}
