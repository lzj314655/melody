package com.melody.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.common.R;
import com.melody.entity.TOrder;
import com.melody.entity.TOrderItem;
import com.melody.mapper.TOrderItemMapper;
import com.melody.service.IOrderService;
import com.melody.service.impl.OperationLogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台订单管理（PRD §8.2 /api/admin/order，F-A-030 ~ F-A-035）
 */
@RestController
@RequestMapping("/api/admin/order")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
@RequiredArgsConstructor
public class AdminOrderController {

    private final IOrderService orderService;
    private final TOrderItemMapper orderItemMapper;
    private final ObjectMapper objectMapper;
    private final OperationLogServiceImpl opLog;

    /** F-A-030 订单列表（带 items） */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('order:read')")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false, defaultValue = "ALL") String status) {
        List<TOrder> orders = orderService.listAll(status);
        return R.ok(attachItems(orders));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:read')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        TOrder o = orderService.getById(id);
        return R.ok(attachItems(Collections.singletonList(o)).get(0));
    }

    /** F-A-032 发货 */
    @PutMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('order:ship')")
    public R<Void> ship(@PathVariable Long id, @RequestParam String trackingNo) {
        orderService.ship(id, trackingNo);
        opLog.log("order", "发货订单 id=" + id + "，物流单号 " + trackingNo);
        return R.<Void>okMsg("已发货");
    }

    /** F-A-033 退款审核通过 */
    @PutMapping("/{id}/refund-approve")
    @PreAuthorize("hasAuthority('order:refund')")
    public R<Void> approveRefund(@PathVariable Long id) {
        orderService.approveRefund(id);
        opLog.log("order", "审核通过订单 id=" + id + " 退款申请");
        return R.<Void>okMsg("退款审核通过，已退回钱包");
    }

    private List<Map<String, Object>> attachItems(List<TOrder> orders) {
        if (orders == null || orders.isEmpty()) return new ArrayList<>();
        List<Long> orderIds = orders.stream().map(TOrder::getId).collect(Collectors.toList());
        // 注意：selectBatchIds 是按主键 id 查，这里要按 order_id 查，所以用 in 条件
        List<TOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<TOrderItem>().in(TOrderItem::getOrderId, orderIds));
        Map<Long, List<TOrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(TOrderItem::getOrderId));
        TypeReference<Map<String, Object>> ref = new TypeReference<Map<String, Object>>() {};
        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (TOrder o : orders) {
            Map<String, Object> map = objectMapper.convertValue(o, ref);
            map.put("items", grouped.getOrDefault(o.getId(), Collections.emptyList()));
            result.add(map);
        }
        return result;
    }
}