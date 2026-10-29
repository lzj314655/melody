package com.melody.service.impl;

import com.melody.entity.UserBehaviorLog;
import com.melody.entity.enums.BehaviorTypeEnum;
import com.melody.mapper.UserBehaviorLogMapper;
import com.melody.service.IBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 行为埋点实现：异步落库，不阻塞主流程（PRD §4.3.1）
 *
 * 注意：需要在启动类加 @EnableAsync
 */
@Service
@RequiredArgsConstructor
public class BehaviorServiceImpl implements IBehaviorService {

    private final UserBehaviorLogMapper logMapper;

    @Override
    @Async
    public void report(Long userId, BehaviorTypeEnum type, Long productId, Map<String, Object> extra) {
        UserBehaviorLog log = new UserBehaviorLog();
        log.setUserId(userId);
        log.setProductId(productId);
        log.setBehaviorType(type.getCode());
        log.setWeight(type.getWeight());
        log.setExtra(extra);
        log.setClientIp(null);  // 可在 Controller 注入 HttpServletRequest 后填入
        logMapper.insert(log);
    }
}