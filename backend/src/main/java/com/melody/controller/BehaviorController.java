package com.melody.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.AdminDTOs;
import com.melody.entity.TProduct;
import com.melody.entity.UserBehaviorLog;
import com.melody.entity.enums.BehaviorTypeEnum;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.UserBehaviorLogMapper;
import com.melody.service.IBehaviorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户行为埋点接口（PRD §8.1 /api/behavior，F-R-001）
 *
 * 路径已配置为 permitAll，游客也能上报（userId 为 NULL）。
 */
@Slf4j
@RestController
@RequestMapping("/api/behavior")
@RequiredArgsConstructor
public class BehaviorController {

    private final IBehaviorService behaviorService;
    private final ObjectMapper objectMapper;
    private final UserBehaviorLogMapper logMapper;
    private final TProductMapper productMapper;

    @PostMapping("/report")
    public R<Void> report(@RequestBody AdminDTOs.BehaviorReportRequest req,
                          HttpServletRequest request) {
        BehaviorTypeEnum type;
        try {
            type = BehaviorTypeEnum.valueOf(req.getBehaviorType());
        } catch (Exception ex) {
            return R.fail("行为类型不合法");
        }
        Map<String, Object> extra = null;
        if (req.getExtra() != null && !req.getExtra().isBlank()) {
            try {
                extra = objectMapper.readValue(req.getExtra(), new TypeReference<>() {});
            } catch (Exception ignore) {}
        }
        Long userId = SecurityUtils.currentId(); // 游客为 null
        behaviorService.report(userId, type, req.getProductId(), extra);
        return R.ok();
    }

    /**
     * F-U-XXX 当前登录用户最近的浏览商品（去重 productId，按最近一次浏览时间倒序，最多 20 条）
     */
    @GetMapping("/recent-views")
    public R<List<TProduct>> recentViews(@RequestParam(defaultValue = "20") Integer limit) {
        Long userId = SecurityUtils.currentId();
        if (userId == null) {
            return R.fail("未登录");
        }
        int n = Math.min(Math.max(limit, 1), 50);

        // 1. 拉最近的 VIEW 行为，按 created_at DESC；同秒内 id DESC 作次级排序保证先后顺序稳定
        //
        // F-U-XXX 只认"用户真正点开商品详情页"的浏览（extra.source = detail）。
        // 列表页 watch(filteredProducts) 会对列表里所有商品批量上报 VIEW（source=list），
        // 那是"曝光"不是"浏览"，且同一批时间戳最新，会淹没真实浏览历史 → 库里堆积大量
        // list 垃圾 VIEW，导致"近期浏览"返回的不是用户真正看过的商品。这里过滤掉。
        // source 为 NULL 的早期记录（旧版本未带 source 的真实浏览）保留，只排除 list/test/uat。
        List<UserBehaviorLog> logs = logMapper.selectList(new LambdaQueryWrapper<UserBehaviorLog>()
                .eq(UserBehaviorLog::getUserId, userId)
                .eq(UserBehaviorLog::getBehaviorType, BehaviorTypeEnum.VIEW.getCode())
                .and(w -> w
                        .apply("JSON_UNQUOTE(JSON_EXTRACT(extra, '$.source')) = {0}", "detail")
                        .or()
                        .apply("JSON_UNQUOTE(JSON_EXTRACT(extra, '$.source')) IS NULL"))
                .orderByDesc(UserBehaviorLog::getCreatedAt)
                .orderByDesc(UserBehaviorLog::getId)
                .last("LIMIT 500")); // 多拉一些用于去重后凑够 limit

        // 2. 去重保留最近一次的 productId
        Map<Long, UserBehaviorLog> latestByProduct = new LinkedHashMap<>();
        for (UserBehaviorLog log : logs) {
            latestByProduct.putIfAbsent(log.getProductId(), log);
            if (latestByProduct.size() >= n) break;
        }

        if (latestByProduct.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        // 3. 关联商品信息
        List<Long> productIds = new ArrayList<>(latestByProduct.keySet());
        List<TProduct> products = productMapper.selectBatchIds(productIds);

        // 4. 按浏览时间倒序返回
        Map<Long, TProduct> productMap = products.stream()
                .collect(Collectors.toMap(TProduct::getId, p -> p, (a, b) -> a));
        List<TProduct> ordered = productIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return R.ok(ordered);
    }
}