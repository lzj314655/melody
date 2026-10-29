package com.melody.service;

import com.melody.entity.TOrder;
import com.melody.entity.TUserAddress;

import java.util.List;

/**
 * 订单 Service（PRD §7.1 状态机 + §7.2 退款规则 + §7.3 积分）
 */
public interface IOrderService {

    /** F-U-040 创建订单（选中的购物车项 → 按支付方式 WALLET 即时扣 / ALIPAY 待付款） */
    TOrder create(Long userId, List<Long> cartItemIds, TUserAddress address, Long userCouponId, String payMethod);

    /** 余额补付 PENDING_PAY 订单（订单详情页"余额支付"按钮） */
    TOrder payByWallet(Long userId, Long orderId);

    /** 支付宝付款确认：原子流转 PENDING_PAY→PENDING_SHIP + 加积分（幂等，主动查询/异步通知共用） */
    void confirmAlipayPaid(String orderNo, String tradeNo);

    /** F-U-043 取消订单（仅 PENDING_PAY 可取消，库存回滚） */
    void cancel(Long userId, Long orderId);

    /** F-U-042 状态流转：PENDING_RECEIVE → COMPLETED（用户确认收货） */
    void confirmDelivery(Long userId, Long orderId);

    /** F-U-044 申请退款（PENDING_RECEIVE / COMPLETED） */
    void refund(Long userId, Long orderId, String reason);

    /** 后台：发货 */
    void ship(Long orderId, String trackingNo);

    /** 后台：退款审核通过（AFTER_SALE → REFUNDED） */
    void approveRefund(Long orderId);

    /** 查询 */
    TOrder getById(Long orderId);

    List<TOrder> listByUser(Long userId, String status);

    List<TOrder> listAll(String status);
}