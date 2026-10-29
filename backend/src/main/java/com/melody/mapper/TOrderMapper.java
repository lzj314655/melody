package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * TOrder 表 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TOrderMapper extends BaseMapper<TOrder> {

    /**
     * 统计某用户对某商品的"已购"订单条目数（用于评价购买校验）
     *
     * "已购" = 订单状态为 PENDING_RECEIVE（已发货待收货）或 COMPLETED（已完成），
     * 不含 PENDING_PAY（未付款）、PENDING_SHIP（待发货）、AFTER_SALE/REFUNDED/CANCELED。
     *
     * 由于单纯 count 可以比查明细轻，先按此签名实现；若业务需要拿到最近订单 id 再扩方法。
     */
    @Select("""
            SELECT COUNT(1)
            FROM t_order_item oi
            JOIN t_order o ON oi.order_id = o.id
            WHERE o.user_id = #{userId}
              AND oi.product_id = #{productId}
              AND o.status IN ('PENDING_RECEIVE', 'COMPLETED')
            """)
    long countPurchasedProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /** 支付宝付款确认：原子流转 PENDING_PAY→PENDING_SHIP 并回填交易号。affected=1 才有效（幂等）。 */
    @Update("UPDATE t_order " +
            "SET status = 'PENDING_SHIP', paid_at = NOW(), trade_no = #{tradeNo}, pay_method = 'ALIPAY' " +
            "WHERE order_no = #{orderNo} AND status = 'PENDING_PAY'")
    int markPaidByAlipay(@Param("orderNo") String orderNo, @Param("tradeNo") String tradeNo);

    /** 余额补付：原子流转 PENDING_PAY→PENDING_SHIP。affected=1 才有效（幂等，扣款失败事务回滚）。 */
    @Update("UPDATE t_order " +
            "SET status = 'PENDING_SHIP', paid_at = NOW(), pay_method = 'WALLET' " +
            "WHERE id = #{orderId} AND status = 'PENDING_PAY'")
    int markPaidByWallet(@Param("orderId") Long orderId);
}
