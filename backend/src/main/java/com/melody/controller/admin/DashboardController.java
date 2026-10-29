package com.melody.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.entity.TOperationLog;
import com.melody.entity.TOrder;
import com.melody.entity.TProduct;
import com.melody.entity.TUser;
import com.melody.entity.UserBehaviorLog;
import com.melody.mapper.TOperationLogMapper;
import com.melody.mapper.TOrderMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TCategoryMapper;
import com.melody.mapper.TUserMapper;
import com.melody.mapper.UserBehaviorLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据大屏接口（PRD §8.2 /api/admin/dashboard，F-D-001 ~ F-D-006）
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAuthority('dashboard:view')")
@RequiredArgsConstructor
public class DashboardController {

    private final TOrderMapper orderMapper;
    private final TProductMapper productMapper;
    private final TUserMapper userMapper;
    private final UserBehaviorLogMapper behaviorLogMapper;
    private final TOperationLogMapper logMapper;
    private final TCategoryMapper categoryMapper;

    @GetMapping("/kpi")
    public R<Map<String, Object>> kpi() {
        List<TOrder> all = orderMapper.selectList(null);
        BigDecimal gmv = all.stream()
                .filter(o -> !"CANCELED".equals(o.getStatus()) && !"PENDING_PAY".equals(o.getStatus()))
                .map(TOrder::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        long todayOrders = all.stream()
                .filter(o -> o.getCreatedAt() != null)
                .filter(o -> !o.getCreatedAt().isBefore(todayStart) && !o.getCreatedAt().isAfter(todayEnd))
                .count();

        // 客单价 = GMV / 完成订单数
        long completedCount = all.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()) || "PENDING_RECEIVE".equals(o.getStatus())
                          || "PENDING_SHIP".equals(o.getStatus()))
                .count();
        BigDecimal avgOrder = completedCount == 0
                ? BigDecimal.ZERO
                : gmv.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP);

        // 活跃用户（最近 7 天登录）
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long activeUsers = userMapper.selectList(
                new LambdaQueryWrapper<TUser>().ge(TUser::getLastLoginAt, sevenDaysAgo)
        ).size();

        long totalUsers = userMapper.selectCount(null);

        // 复购率 = 下过 ≥2 单（有效单）的用户数 / 下过单的用户数
        Map<Long, Long> ordersPerUser = all.stream()
                .filter(o -> o.getUserId() != null)
                .filter(o -> !"CANCELED".equals(o.getStatus()) && !"PENDING_PAY".equals(o.getStatus()))
                .collect(Collectors.groupingBy(TOrder::getUserId, Collectors.counting()));
        long paidUsers = ordersPerUser.size();
        long repeatBuyers = ordersPerUser.values().stream().filter(c -> c >= 2).count();
        String repurchaseRate = paidUsers == 0
                ? "0.0%"
                : String.format("%.1f%%", repeatBuyers * 100.0 / paidUsers);

        // 转化率 = 近 30 天内下过单的用户数 / 近 30 天浏览过商品的去重用户数（口径对齐同一时间窗）
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Long viewUsersDistinct = behaviorLogMapper.selectObjs(
                new LambdaQueryWrapper<UserBehaviorLog>()
                        .select(UserBehaviorLog::getUserId)
                        .eq(UserBehaviorLog::getBehaviorType, "VIEW")
                        .isNotNull(UserBehaviorLog::getUserId)
                        .ge(UserBehaviorLog::getCreatedAt, thirtyDaysAgo)
        ).stream().filter(Objects::nonNull).map(o -> ((Number) o).longValue()).distinct().count();
        long buyers30d = all.stream()
                .filter(o -> o.getUserId() != null && o.getCreatedAt() != null)
                .filter(o -> !"CANCELED".equals(o.getStatus()) && !"PENDING_PAY".equals(o.getStatus()))
                .filter(o -> !o.getCreatedAt().isBefore(thirtyDaysAgo))
                .map(TOrder::getUserId)
                .distinct().count();
        String conversionRate = viewUsersDistinct == 0
                ? "0.0%"
                : String.format("%.1f%%", buyers30d * 100.0 / viewUsersDistinct);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("gmv", gmv);
        kpi.put("todayOrders", todayOrders);
        kpi.put("conversionRate", conversionRate);
        kpi.put("avgOrderAmount", avgOrder);
        kpi.put("repurchaseRate", repurchaseRate);
        kpi.put("activeUsers", activeUsers);
        kpi.put("totalUsers", totalUsers);
        return R.ok(kpi);
    }

    /** GMV 趋势（最近 7 天，按 createdAt 聚合） */
    @GetMapping("/gmv-trend")
    public R<List<Map<String, Object>>> gmvTrend() {
        List<TOrder> all = orderMapper.selectList(null);
        Map<LocalDate, BigDecimal> buckets = new TreeMap<>();
        for (int i = 6; i >= 0; i--) {
            buckets.put(LocalDate.now().minusDays(i), BigDecimal.ZERO);
        }
        for (TOrder o : all) {
            if (o.getCreatedAt() == null) continue;
            if ("CANCELED".equals(o.getStatus()) || "PENDING_PAY".equals(o.getStatus())) continue;
            LocalDate d = o.getCreatedAt().toLocalDate();
            if (buckets.containsKey(d) && o.getTotalPrice() != null) {
                buckets.put(d, buckets.get(d).add(o.getTotalPrice()));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        buckets.forEach((d, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            m.put("value", v);
            out.add(m);
        });
        return R.ok(out);
    }

    /** 订单量趋势（最近 7 天） */
    @GetMapping("/order-trend")
    public R<List<Map<String, Object>>> orderTrend() {
        List<TOrder> all = orderMapper.selectList(null);
        Map<LocalDate, Long> buckets = new TreeMap<>();
        for (int i = 6; i >= 0; i--) {
            buckets.put(LocalDate.now().minusDays(i), 0L);
        }
        for (TOrder o : all) {
            if (o.getCreatedAt() == null) continue;
            LocalDate d = o.getCreatedAt().toLocalDate();
            if (buckets.containsKey(d)) {
                buckets.put(d, buckets.get(d) + 1);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        buckets.forEach((d, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            m.put("value", v);
            out.add(m);
        });
        return R.ok(out);
    }

    /** 商品销售 TOP10（按 sales 字段，仅上架商品） */
    @GetMapping("/top-products")
    public R<List<Map<String, Object>>> topProducts() {
        // status=1 过滤下架商品，避免被删除/下架的商品仍出现在销售排行里
        List<TProduct> products = productMapper.selectList(
                new LambdaQueryWrapper<TProduct>().eq(TProduct::getStatus, 1));
        products.sort((a, b) -> Integer.compare(
                b.getSales() == null ? 0 : b.getSales(),
                a.getSales() == null ? 0 : a.getSales()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TProduct p : products.stream().limit(10).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("sales", p.getSales() == null ? 0 : p.getSales());
            m.put("coverImage", p.getCoverImage());
            out.add(m);
        }
        return R.ok(out);
    }

    /** 系统日志（最近 50 条） */
    @GetMapping("/logs")
    public R<List<TOperationLog>> logs() {
        List<TOperationLog> logs = logMapper.selectList(
                new LambdaQueryWrapper<TOperationLog>().orderByDesc(TOperationLog::getId).last("LIMIT 50"));
        return R.ok(logs);
    }

    /** F-D-005 零食分类受欢迎程度：按在售商品累计销量聚合分类占比 */
    @GetMapping("/category-share")
    public R<List<Map<String, Object>>> categoryShare() {
        return R.ok(categoryMapper.selectCategoryShare());
    }
}