package com.melody.controller;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.entity.TProduct;
import com.melody.service.IProductService;
import com.melody.service.IRecommendService;
import com.melody.task.RecommendTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 推荐接口（PRD §8.1 /api/recommend，F-R-005 / F-R-006 / F-R-004）
 *
 * 公开接口（无 token 也可访问），游客走 coldStart 兜底
 */
@Slf4j
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final IRecommendService recommendService;
    private final IProductService productService;
    private final RecommendTask recommendTask;

    /** F-R-005 首页"为你推荐" */
    @GetMapping("/personal")
    public R<List<TProduct>> personal(@RequestParam(defaultValue = "10") Integer limit) {
        Long uid = SecurityUtils.currentId(); // 游客返回 null → 自动走 coldStart
        log.info("[Recommend] /personal incoming uid={} limit={}", uid, limit);
        List<Long> ids = recommendService.personalRecommend(uid, limit);
        return R.ok(toProducts(ids));
    }

    /** F-R-006 详情页"猜你喜欢" */
    @GetMapping("/similar/{productId}")
    public R<List<TProduct>> similar(@PathVariable Long productId,
                                     @RequestParam(defaultValue = "6") Integer limit) {
        List<Long> ids = recommendService.similarRecommend(productId, limit);
        return R.ok(toProducts(ids));
    }

    /** F-R-004 冷启动（全局热门 TOP20） */
    @GetMapping("/cold-start")
    public R<List<TProduct>> coldStart(@RequestParam(defaultValue = "20") Integer limit) {
        List<Long> ids = recommendService.coldStart(limit);
        return R.ok(toProducts(ids));
    }

    /** 管理员：手动触发重算（PRD F-R-007 推荐效果埋点 / 调优） */
    @PostMapping("/admin/refresh")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public R<Integer> refresh() {
        int affected = recommendTask.rebuildAll();
        return R.ok("推荐已重算", affected);
    }

    /** ID → Product 实体（按原顺序） */
    private List<TProduct> toProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<TProduct> all = productService.list(null, null, "default");
        return ids.stream()
                .map(id -> all.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null))
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }
}