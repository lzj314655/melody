package com.melody.service;

import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;

import java.util.List;

/**
 * 商品 Service
 */
public interface IProductService {

    /** F-U-011 商品分类（顶级） */
    List<TCategory> listCategories();

    /** F-U-020/F-U-022/F-U-023 商品列表（分类筛选 + 关键词搜索 + 排序） */
    List<TProduct> list(Long categoryId, String keyword, String sortBy);

    /** F-U-021 商品详情 */
    TProduct getDetail(Long id);

    /** F-U-027 SKU 列表（按商品） */
    List<TProductSku> listSkus(Long productId);

    /** 后台：新增 */
    TProduct create(TProduct product, List<TProductSku> skus);

    /** 后台：编辑 */
    TProduct update(Long id, TProduct patch);

    /** 后台：SKU 同步（编辑商品用）— 带 id 的按 id 更新/无 id 的新增，前端未传的旧 SKU 下架（保护历史订单） */
    void syncSkus(Long productId, List<TProductSku> skus);

    /** 后台：删除（下架） */
    void delete(Long id);

    /** 后台：创建分类 */
    TCategory createCategory(TCategory c);

    /** 后台：更新分类 */
    TCategory updateCategory(Long id, TCategory patch);

    /** 后台：删除分类 */
    void deleteCategory(Long id);
}