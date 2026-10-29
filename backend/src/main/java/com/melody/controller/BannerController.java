package com.melody.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.entity.TBanner;
import com.melody.mapper.TBannerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台首页轮播图接口 (F-U-010)
 *
 * 只返回 status=1 且当前时间在生效区间内的轮播图，按 sort 升序。
 * 前台首页 /api/banner/list 由前端 recommend store 拉取，无需鉴权（见 SecurityConfig 白名单）。
 */
@RestController
@RequiredArgsConstructor
public class BannerController {

    private final TBannerMapper bannerMapper;

    @GetMapping("/api/banner/list")
    public R<List<TBanner>> list() {
        LocalDateTime now = LocalDateTime.now();
        List<TBanner> banners = bannerMapper.selectList(
                new LambdaQueryWrapper<TBanner>()
                        .eq(TBanner::getStatus, 1)
                        .and(w -> w.isNull(TBanner::getStartDate).or().le(TBanner::getStartDate, now))
                        .and(w -> w.isNull(TBanner::getEndDate).or().ge(TBanner::getEndDate, now))
                        .orderByAsc(TBanner::getSort)
        );
        return R.ok(banners);
    }
}