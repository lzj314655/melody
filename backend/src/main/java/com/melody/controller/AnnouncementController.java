package com.melody.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.entity.TAnnouncement;
import com.melody.mapper.TAnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台公告接口 (F-A-063)
 *
 * 只返回 status=1（已发布）且未过期（expire_at 为 null 或 > now）的公告，
 * 置顶优先（is_top DESC），再按 publish_at DESC。
 */
@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final TAnnouncementMapper announcementMapper;

    @GetMapping("/api/announcement/list")
    public R<List<TAnnouncement>> list() {
        LocalDateTime now = LocalDateTime.now();
        List<TAnnouncement> list = announcementMapper.selectList(
                new LambdaQueryWrapper<TAnnouncement>()
                        .eq(TAnnouncement::getStatus, 1)
                        .and(w -> w.isNull(TAnnouncement::getExpireAt).or().gt(TAnnouncement::getExpireAt, now))
                        .orderByDesc(TAnnouncement::getIsTop)
                        .orderByDesc(TAnnouncement::getPublishAt)
        );
        return R.ok(list);
    }
}