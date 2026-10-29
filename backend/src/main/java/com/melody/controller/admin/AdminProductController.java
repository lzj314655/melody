package com.melody.controller.admin;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.service.IProductService;
import com.melody.service.impl.OperationLogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台商品 + 分类管理（PRD §8.2 /api/admin/product + /api/admin/sku + /api/admin/category）
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
@RequiredArgsConstructor
public class AdminProductController {

    private final IProductService productService;
    private final OperationLogServiceImpl opLog;

    /** 本地商品图片根目录（与 WebMvcConfig 的 /img/** 静态映射一致） */
    @Value("${app.image-root:D:\\bishe\\image}")
    private String imageRoot;

    /** F-A-020 商品列表 */
    @GetMapping("/product/list")
    @PreAuthorize("hasAuthority('product:read')")
    public R<List<TProduct>> list(@RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) String keyword) {
        return R.ok(productService.list(categoryId, keyword, "default"));
    }

    /** F-A-021 新增商品 */
    @PostMapping("/product/create")
    @PreAuthorize("hasAuthority('product:create')")
    public R<TProduct> create(@RequestBody ProductCreateRequest req) {
        TProduct saved = productService.create(req.getProduct(), req.getSkus());
        opLog.log("product", "上架商品「" + (saved.getName() == null ? "" : saved.getName()) + "」");
        return R.ok(saved);
    }

    /** F-A-022 编辑商品（商品本体 + 可选 SKU 同步：按 id 更新/新增，前端未传的旧 SKU 下架） */
    @PutMapping("/product/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public R<TProduct> update(@PathVariable Long id, @RequestBody ProductUpdateRequest req) {
        TProduct saved = productService.update(id, req.getProduct());
        if (req.getSkus() != null) {
            productService.syncSkus(id, req.getSkus());
        }
        opLog.log("product", "更新商品 id=" + id + " 信息");
        return R.ok(saved);
    }

    /** F-A-023 下架 */
    @DeleteMapping("/product/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        opLog.log("product", "下架商品 id=" + id);
        return R.<Void>okMsg("下架成功");
    }

    /** SKU 管理 */
    @GetMapping("/sku")
    @PreAuthorize("hasAuthority('product:read')")
    public R<List<TProductSku>> skus(@RequestParam Long productId) {
        return R.ok(productService.listSkus(productId));
    }

    /** 分类 */
    @GetMapping("/category/list")
    @PreAuthorize("hasAuthority('category:manage')")
    public R<List<TCategory>> categories() {
        return R.ok(productService.listCategories());
    }

    /** 创建分类 */
    @PostMapping("/category/create")
    @PreAuthorize("hasAuthority('category:manage')")
    public R<TCategory> createCategory(@RequestBody TCategory c) {
        TCategory saved = productService.createCategory(c);
        opLog.log("category", "创建分类「" + (saved.getName() == null ? "" : saved.getName()) + "」");
        return R.ok("创建成功", saved);
    }

    /** 更新分类 */
    @PutMapping("/category/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public R<TCategory> updateCategory(@PathVariable Long id, @RequestBody TCategory patch) {
        TCategory saved = productService.updateCategory(id, patch);
        opLog.log("category", "更新分类 id=" + id);
        return R.ok("更新成功", saved);
    }

    /** 停用分类 */
    @DeleteMapping("/category/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public R<Void> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        opLog.log("category", "停用分类 id=" + id);
        return R.<Void>okMsg("已停用");
    }

    /** 图片库路径选择：递归列出 imageRoot 下的本地图片，供上架/编辑商品时选择配图 */
    @GetMapping("/product/image/list")
    public R<List<Map<String, String>>> imageList() {
        List<Map<String, String>> list = new ArrayList<>();
        File root = new File(imageRoot);
        if (root.isDirectory()) {
            collectImages(root, "", list);
        }
        return R.ok(list);
    }

    private void collectImages(File dir, String prefix, List<Map<String, String>> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                collectImages(f, prefix + f.getName() + "/", out);
            } else if (isImage(f.getName())) {
                Map<String, String> m = new HashMap<>();
                m.put("name", prefix + f.getName());
                m.put("url", "/img/" + prefix + f.getName());
                out.add(m);
            }
        }
    }

    private boolean isImage(String name) {
        String l = name.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png")
                || l.endsWith(".webp") || l.endsWith(".gif");
    }

    /** 复合请求体：商品 + SKU 列表 */
    @lombok.Data
    public static class ProductCreateRequest {
        private TProduct product;
        private List<TProductSku> skus;
    }

    /** 编辑请求体：商品本体 + 可选 SKU 列表（带 id 的按 id 更新，无 id 的新增） */
    @lombok.Data
    public static class ProductUpdateRequest {
        private TProduct product;
        private List<TProductSku> skus;
    }
}