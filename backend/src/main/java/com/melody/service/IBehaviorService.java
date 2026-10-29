package com.melody.service;

import com.melody.entity.enums.BehaviorTypeEnum;

import java.util.Map;

/**
 * 用户行为埋点 Service（F-R-001）
 */
public interface IBehaviorService {

    /** F-R-001 上报一条行为 */
    void report(Long userId, BehaviorTypeEnum type, Long productId, Map<String, Object> extra);
}