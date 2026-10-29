package com.melody.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.entity.TFavorite;
import com.melody.mapper.TFavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收藏接口（F-U-038 / F-U-039）
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final TFavoriteMapper favoriteMapper;

    /** 我的收藏（返回商品 id 列表） */
    @GetMapping
    public R<List<Long>> myFavorites() {
        Long userId = SecurityUtils.currentId();
        if (userId == null) return R.ok(List.of());
        List<TFavorite> list = favoriteMapper.selectList(
                new LambdaQueryWrapper<TFavorite>().eq(TFavorite::getUserId, userId));
        return R.ok(list.stream().map(TFavorite::getProductId).collect(Collectors.toList()));
    }

    /** 添加收藏（幂等） */
    @PostMapping("/{productId}")
    public R<Void> add(@PathVariable Long productId) {
        Long userId = SecurityUtils.currentId();
        if (userId == null) return R.<Void>fail(1002, "请先登录");
        try {
            TFavorite f = new TFavorite();
            f.setUserId(userId);
            f.setProductId(productId);
            favoriteMapper.insert(f);
        } catch (DuplicateKeyException ignore) {
            // 已存在
        }
        return R.<Void>okMsg("已收藏");
    }

    /** 取消收藏 */
    @DeleteMapping("/{productId}")
    public R<Void> remove(@PathVariable Long productId) {
        Long userId = SecurityUtils.currentId();
        if (userId == null) return R.<Void>fail(1002, "请先登录");
        favoriteMapper.delete(new LambdaQueryWrapper<TFavorite>()
                .eq(TFavorite::getUserId, userId)
                .eq(TFavorite::getProductId, productId));
        return R.<Void>okMsg("已取消");
    }

    /** 批量判断是否收藏（返回 Set<productId>） */
    @PostMapping("/check")
    public R<Map<String, Object>> check(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.currentId();
        if (userId == null) return R.ok(Map.of("ids", List.of()));
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("productIds", List.of());
        List<Long> ids = raw.stream().map(o -> Long.parseLong(String.valueOf(o))).collect(Collectors.toList());
        if (ids.isEmpty()) return R.ok(Map.of("ids", List.of()));
        List<TFavorite> list = favoriteMapper.selectList(
                new LambdaQueryWrapper<TFavorite>()
                        .eq(TFavorite::getUserId, userId)
                        .in(TFavorite::getProductId, ids));
        return R.ok(Map.of("ids", list.stream().map(TFavorite::getProductId).collect(Collectors.toList())));
    }
}