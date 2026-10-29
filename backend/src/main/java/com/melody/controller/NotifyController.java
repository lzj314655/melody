package com.melody.controller;

import com.melody.service.OrderNotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 实时通知 SSE 流（后台管理页订阅新订单事件）。
 * 路径已在 SecurityConfig permitAll（事件只含提示信息，无需鉴权）。
 */
@RestController
@RequiredArgsConstructor
public class NotifyController {

    private final OrderNotifyService orderNotifyService;

    /** 订阅通知流：前台有新订单下单成功 → 后台立即收到 NEW_ORDER 事件 */
    @GetMapping("/api/notify/stream")
    public SseEmitter stream() {
        return orderNotifyService.subscribe();
    }
}
