package com.melody.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.CheckoutDTOs;
import com.melody.dto.RechargeDTOs;
import com.melody.entity.TOrder;
import com.melody.entity.TOrderItem;
import com.melody.entity.TUserAddress;
import com.melody.mapper.TOrderItemMapper;
import com.melody.mapper.TUserAddressMapper;
import com.melody.service.IAlipayService;
import com.melody.service.IOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 订单接口（PRD §8.1 /api/order，F-U-040 ~ F-U-044）
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;
    private final TUserAddressMapper addressMapper;
    private final TOrderItemMapper orderItemMapper;
    // 注入 Spring 容器管理的 ObjectMapper（含 JavaTimeModule 等定制）
    private final ObjectMapper objectMapper;
    private final IAlipayService alipayService;

    /** 查我的订单（按状态过滤），并把明细 items 拼在每个 order 上 */
    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false, defaultValue = "ALL") String status) {
        List<TOrder> orders = orderService.listByUser(SecurityUtils.currentId(), status);
        List<Map<String, Object>> result = attachItems(orders);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        TOrder o = orderService.getById(id);
        Map<String, Object> map = attachItems(Collections.singletonList(o)).get(0);
        return R.ok(map);
    }

    /** F-U-040 提交订单 */
    @PostMapping
    public R<TOrder> create(@Valid @RequestBody CheckoutDTOs.CheckoutRequest req) {
        TUserAddress addr = addressMapper.selectById(req.getAddressId());
        if (addr == null) throw new IllegalArgumentException("收货地址不存在");
        return R.ok("下单成功", orderService.create(SecurityUtils.currentId(),
                req.getCartItemIds(), addr, req.getUserCouponId(), req.getPayMethod()));
    }

    /** F-U-043 取消订单 */
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(SecurityUtils.currentId(), id);
        return R.<Void>okMsg("订单已取消");
    }

    /** F-U-042 确认收货 */
    @PutMapping("/{id}/confirm")
    public R<Void> confirmDelivery(@PathVariable Long id) {
        orderService.confirmDelivery(SecurityUtils.currentId(), id);
        return R.<Void>okMsg("已确认收货");
    }

    /** F-U-044 申请退款 */
    @PostMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id, @Valid @RequestBody CheckoutDTOs.RefundRequest req) {
        orderService.refund(SecurityUtils.currentId(), id, req.getReason());
        return R.<Void>okMsg("退款申请已提交");
    }

    /** PENDING_PAY 订单用余额补付 */
    @PostMapping("/{id}/wallet-pay")
    public R<TOrder> walletPay(@PathVariable Long id) {
        return R.ok("支付成功", orderService.payByWallet(SecurityUtils.currentId(), id));
    }

    /** PENDING_PAY 订单生成支付宝支付页表单（out_trade_no = order_no） */
    @PostMapping("/{id}/alipay-pay")
    public R<RechargeDTOs.OrderPayFormVO> alipayPay(@PathVariable Long id) {
        return R.ok(alipayService.createOrderPayForm(SecurityUtils.currentId(), id));
    }

    /** 主动查询订单支付状态，已付款则流转 PENDING_PAY→PENDING_SHIP（幂等） */
    @GetMapping("/{id}/alipay-query")
    public R<RechargeDTOs.OrderPayQueryVO> alipayQuery(@PathVariable Long id) {
        return R.ok(alipayService.queryOrderPay(SecurityUtils.currentId(), id));
    }

    /** 支付宝异步通知（公开）：验签 + 流转，返回纯文本 success/fail */
    @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String orderNotify(@RequestParam Map<String, String> params) {
        return alipayService.handleOrderNotify(params);
    }

    // 把 TOrder 转成 Map，附加 items 列表
    private List<Map<String, Object>> attachItems(List<TOrder> orders) {
        if (orders == null || orders.isEmpty()) return new ArrayList<>();
        List<Long> orderIds = orders.stream().map(TOrder::getId).collect(Collectors.toList());
        // 按 order_id 过滤，不是主键
        List<TOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<TOrderItem>().in(TOrderItem::getOrderId, orderIds));
        Map<Long, List<TOrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(TOrderItem::getOrderId));
        com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> ref =
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {};
        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (TOrder o : orders) {
            Map<String, Object> map = objectMapper.convertValue(o, ref);
            map.put("items", grouped.getOrDefault(o.getId(), Collections.emptyList()));
            result.add(map);
        }
        return result;
    }
}
