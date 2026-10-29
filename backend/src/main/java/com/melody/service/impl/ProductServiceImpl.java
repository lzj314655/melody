package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.melody.exception.BizException;
import com.melody.common.ResultCode;
import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.mapper.TCategoryMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductSkuMapper;
import com.melody.service.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements IProductService {

    private final TProductMapper productMapper;
    private final TProductSkuMapper skuMapper;
    private final TCategoryMapper categoryMapper;

    @Override
    public List<TCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<TCategory>()
                .eq(TCategory::getStatus, 1)
                .orderByAsc(TCategory::getSort));
    }

    @Override
    public List<TProduct> list(Long categoryId, String keyword, String sortBy) {
        QueryWrapper<TProduct> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("name", keyword).or().like("description", keyword));
        }
        switch (sortBy == null ? "default" : sortBy) {
            case "price-asc" -> qw.orderByAsc("price");
            case "price-desc" -> qw.orderByDesc("price");
            case "sales-desc" -> qw.orderByDesc("sales");
            case "rating-desc" -> qw.orderByDesc("rating");
            default -> qw.orderByDesc("id");
        }
        List<TProduct> list = productMapper.selectList(qw);
        // 剩余件数 = 各在架 SKU 库存之和（下单扣 SKU 库存，t_product.inventory 与此保持一致）
        Map<Long, Long> stockSum = skuMapper.selectList(new LambdaQueryWrapper<TProductSku>()
                        .eq(TProductSku::getStatus, 1))
                .stream()
                .collect(Collectors.groupingBy(TProductSku::getProductId,
                        Collectors.summingLong(s -> s.getStock() == null ? 0 : s.getStock())));
        for (TProduct p : list) {
            Long s = stockSum.get(p.getId());
            p.setInventory(s == null ? 0 : s.intValue());
        }
        return list;
    }

    @Override
    public TProduct getDetail(Long id) {
        TProduct p = productMapper.selectById(id);
        if (p == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        return p;
    }

    @Override
    public List<TProductSku> listSkus(Long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<TProductSku>()
                .eq(TProductSku::getProductId, productId)
                .eq(TProductSku::getStatus, 1));
    }

    @Override
    @Transactional
    public TProduct create(TProduct product, List<TProductSku> skus) {
        if (product.getName() == null || product.getPrice() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品名与价格必填");
        }
        product.setId(null);
        product.setStatus(product.getStatus() == null ? 1 : product.getStatus());
        product.setSales(0);
        // 剩余件数 = 各 SKU 库存之和（覆盖前端写死的 500）
        int totalStock = 0;
        if (skus != null) {
            for (TProductSku sku : skus) {
                totalStock += sku.getStock() == null ? 0 : sku.getStock();
            }
        }
        product.setInventory(totalStock);
        if (product.getRating() == null) product.setRating(new java.math.BigDecimal("5.00"));
        productMapper.insert(product);
        if (skus != null && !skus.isEmpty()) {
            for (TProductSku sku : skus) {
                sku.setId(null);
                sku.setProductId(product.getId());
                if (sku.getStatus() == null) sku.setStatus(1);
                if (sku.getSales() == null) sku.setSales(0);
                skuMapper.insert(sku);
            }
        }
        return product;
    }

    @Override
    @Transactional
    public TProduct update(Long id, TProduct patch) {
        TProduct old = productMapper.selectById(id);
        if (old == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        // 白名单更新
        old.setName(patch.getName());
        old.setCategoryId(patch.getCategoryId());
        old.setBrand(patch.getBrand());
        old.setPrice(patch.getPrice());
        old.setMarketPrice(patch.getMarketPrice());
        old.setDescription(patch.getDescription());
        old.setCoverImage(patch.getCoverImage());
        old.setInventory(patch.getInventory());
        old.setTags(patch.getTags());
        old.setOrigin(patch.getOrigin());
        old.setShelfLife(patch.getShelfLife());
        old.setWeight(patch.getWeight());
        old.setIngredients(patch.getIngredients());
        productMapper.updateById(old);
        return old;
    }

    /**
     * SKU 同步（编辑商品时）。规则：
     *   - 带 id 且属于本商品的 SKU → 按 id 更新规格名/价格/库存
     *   - 无 id 的 → 新增
     *   - 数据库中已存在但前端未传的 SKU → 下架（status=0，不物理删，保护历史订单的 skuId 引用）
     */
    @Override
    @Transactional
    public void syncSkus(Long productId, List<TProductSku> skus) {
        if (skus == null || skus.isEmpty()) return;
        List<TProductSku> existing = skuMapper.selectList(new LambdaQueryWrapper<TProductSku>()
                .eq(TProductSku::getProductId, productId));
        java.util.Map<Long, TProductSku> oldMap = existing.stream()
                .collect(java.util.stream.Collectors.toMap(TProductSku::getId, s -> s));
        java.util.Set<Long> kept = new java.util.HashSet<>();
        for (TProductSku sku : skus) {
            if (sku.getId() != null && oldMap.containsKey(sku.getId())) {
                // 按 id 更新（仅允许更新本商品的 SKU）
                TProductSku o = oldMap.get(sku.getId());
                if (!productId.equals(o.getProductId())) continue;
                kept.add(sku.getId());
                if (sku.getSpecName() != null) o.setSpecName(sku.getSpecName());
                if (sku.getPrice() != null) o.setPrice(sku.getPrice());
                if (sku.getStock() != null) o.setStock(sku.getStock());
                if (sku.getStatus() != null) o.setStatus(sku.getStatus());
                skuMapper.updateById(o);
            } else {
                // 无 id → 新增
                TProductSku n = new TProductSku();
                n.setProductId(productId);
                n.setSpecName(sku.getSpecName());
                n.setPrice(sku.getPrice());
                n.setStock(sku.getStock() == null ? 0 : sku.getStock());
                n.setSales(0);
                n.setStatus(sku.getStatus() == null ? 1 : sku.getStatus());
                skuMapper.insert(n);
            }
        }
        // 未在本次提交中的旧 SKU → 下架
        for (TProductSku o : existing) {
            if (!kept.contains(o.getId()) && o.getStatus() != null && o.getStatus() == 1) {
                o.setStatus(0);
                skuMapper.updateById(o);
            }
        }
        // 剩余件数 = 在架 SKU 库存之和，保持 t_product.inventory 与真实库存一致
        List<TProductSku> all = skuMapper.selectList(new LambdaQueryWrapper<TProductSku>()
                .eq(TProductSku::getProductId, productId)
                .eq(TProductSku::getStatus, 1));
        int total = all.stream().mapToInt(s -> s.getStock() == null ? 0 : s.getStock()).sum();
        TProduct p = productMapper.selectById(productId);
        if (p != null) {
            p.setInventory(total);
            productMapper.updateById(p);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // PRD F-A-023: 软删除 = 改为下架 status=0
        TProduct p = productMapper.selectById(id);
        if (p == null) throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        p.setStatus(0);
        productMapper.updateById(p);
    }

    @Override
    @Transactional
    public TCategory createCategory(TCategory c) {
        if (c.getName() == null || c.getName().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "分类名必填");
        }
        c.setId(null);
        if (c.getStatus() == null) c.setStatus(1);
        if (c.getSort() == null) c.setSort(0);
        categoryMapper.insert(c);
        return c;
    }

    @Override
    @Transactional
    public TCategory updateCategory(Long id, TCategory patch) {
        TCategory old = categoryMapper.selectById(id);
        if (old == null) throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getIcon() != null) old.setIcon(patch.getIcon());
        if (patch.getSort() != null) old.setSort(patch.getSort());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getParentId() != null) old.setParentId(patch.getParentId());
        categoryMapper.updateById(old);
        return old;
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        TCategory c = categoryMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        c.setStatus(0);
        categoryMapper.updateById(c);
    }
}