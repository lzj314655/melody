package com.melody.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.melody.entity.TOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 后台实时通知（SSE 广播）。
 *
 * 前台下单成功后由 OrderServiceImpl 在事务提交后调用 broadcastNewOrder，
 * 后台管理页通过 GET /api/notify/stream 订阅，有新订单立即弹通知。
 * 事件只含提示信息（订单号/金额），不含订单明细，接口放行无需鉴权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotifyService {

    private final ObjectMapper objectMapper;

    /** 活跃的 SSE 连接（后台管理页，一般 1~2 个） */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    /** 注册一个 SSE 连接（不超时，断线自动移除） */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        long id = seq.incrementAndGet();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));
        return emitter;
    }

    /** 广播新订单事件（事务提交后调用） */
    public void broadcastNewOrder(TOrder order) {
        if (emitters.isEmpty()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("totalPrice", order.getTotalPrice());
        payload.put("payMethod", order.getPayMethod());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[Notify] 序列化新订单事件失败: {}", e.getMessage());
            return;
        }
        emitters.values().removeIf(em -> {
            try {
                em.send(SseEmitter.event().name("NEW_ORDER").data(json));
                return false;
            } catch (IOException | IllegalStateException e) {
                return true; // 连接已断开，移除
            }
        });
    }
}
