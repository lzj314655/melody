package com.melody.controller;

import com.melody.common.R;
import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.service.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台商品接口（PRD §8.1 /api/product + /api/sku）
 */
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final IProductService productService;

    /** F-U-011 商品分类 */
    @GetMapping("/api/product/categories")
    public R<List<TCategory>> categories() {
        return R.ok(productService.listCategories());
    }

    /** F-U-020 / F-U-022 / F-U-023 商品列表（分类/搜索/排序） */
    @GetMapping("/api/product/list")
    public R<List<TProduct>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "default") String sort) {
        return R.ok(productService.list(categoryId, keyword, sort));
    }

    /** F-U-021 商品详情 */
    @GetMapping("/api/product/detail/{id}")
    public R<TProduct> detail(@PathVariable Long id) {
        return R.ok(productService.getDetail(id));
    }

    /** F-U-027 SKU 列表 */
    @GetMapping("/api/sku")
    public R<List<TProductSku>> skus(@RequestParam Long productId) {
        return R.ok(productService.listSkus(productId));
    }
}