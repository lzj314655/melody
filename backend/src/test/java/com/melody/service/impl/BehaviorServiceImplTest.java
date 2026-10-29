package com.melody.service.impl;

import com.melody.entity.UserBehaviorLog;
import com.melody.entity.enums.BehaviorTypeEnum;
import com.melody.mapper.UserBehaviorLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * BehaviorServiceImpl 单元测试：report 把 BehaviorTypeEnum 转为 code/weight 后落库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BehaviorServiceImpl 单元测试")
class BehaviorServiceImplTest {

    @Mock private UserBehaviorLogMapper logMapper;

    @InjectMocks
    private BehaviorServiceImpl behaviorService;

    @Test
    @DisplayName("report VIEW → 落库 code=VIEW, weight=1")
    void reportView() {
        behaviorService.report(1L, BehaviorTypeEnum.VIEW, 100L, null);

        ArgumentCaptor<UserBehaviorLog> captor = ArgumentCaptor.forClass(UserBehaviorLog.class);
        verify(logMapper).insert(captor.capture());

        UserBehaviorLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(1L);
        assertThat(log.getProductId()).isEqualTo(100L);
        assertThat(log.getBehaviorType()).isEqualTo("VIEW");
        assertThat(log.getWeight()).isEqualTo(1);
    }

    @Test
    @DisplayName("report ORDER → 落库 code=ORDER, weight=5")
    void reportOrder() {
        behaviorService.report(2L, BehaviorTypeEnum.ORDER, 200L, new HashMap<>(Map.of("qty", 3)));

        ArgumentCaptor<UserBehaviorLog> captor = ArgumentCaptor.forClass(UserBehaviorLog.class);
        verify(logMapper).insert(captor.capture());

        UserBehaviorLog log = captor.getValue();
        assertThat(log.getBehaviorType()).isEqualTo("ORDER");
        assertThat(log.getWeight()).isEqualTo(5);
        assertThat(log.getExtra()).containsEntry("qty", 3);
    }

    @Test
    @DisplayName("report ADD_CART → 落库 code=ADD_CART, weight=3")
    void reportAddCart() {
        behaviorService.report(1L, BehaviorTypeEnum.ADD_CART, 100L, null);

        ArgumentCaptor<UserBehaviorLog> captor = ArgumentCaptor.forClass(UserBehaviorLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getWeight()).isEqualTo(3);
    }

    @Test
    @DisplayName("report FAVORITE → weight=4")
    void reportFavorite() {
        behaviorService.report(1L, BehaviorTypeEnum.FAVORITE, 100L, null);

        ArgumentCaptor<UserBehaviorLog> captor = ArgumentCaptor.forClass(UserBehaviorLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getWeight()).isEqualTo(4);
    }

    @Test
    @DisplayName("report REVIEW → weight=2")
    void reportReview() {
        behaviorService.report(1L, BehaviorTypeEnum.REVIEW, 100L, null);

        ArgumentCaptor<UserBehaviorLog> captor = ArgumentCaptor.forClass(UserBehaviorLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getWeight()).isEqualTo(2);
    }
}
