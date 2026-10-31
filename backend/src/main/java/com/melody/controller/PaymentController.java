package com.melody.controller;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付接口（PRD §8.1 /api/payment）
 *
 * F-U-045 已在 OrderService.create 内钱包自动扣款完成。
 * 这里保留查询入口给前端「支付结果轮询」。
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IOrderService orderService;

    @GetMapping("/{orderId}/status")
    public R<Map<String, Object>> status(@PathVariable Long orderId) {
        var o = orderService.getByIdForUser(orderId, SecurityUtils.currentId());
        return R.ok(Map.of(
                "orderId", o.getId(),
                "status", o.getStatus(),
                "paidAt", o.getPaidAt() == null ? "" : o.getPaidAt().toString()
        ));
    }

    /** 演示：主动查询当前用户钱包余额 */
    @GetMapping("/wallet/balance")
    public R<Object> balance() {
        return R.ok(Map.of("userId", SecurityUtils.currentId()));
    }
}