package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TWalletRechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 钱包充值订单 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TWalletRechargeOrderMapper extends BaseMapper<TWalletRechargeOrder> {

    /**
     * 原子地把订单 PENDING→PAID 并回填支付宝交易号。
     * affected = 1 表示本次确认有效（可加余额）；= 0 表示已处理过或非待付（幂等跳过）。
     */
    @Update("UPDATE t_wallet_recharge_order " +
            "SET status = 'PAID', trade_no = #{tradeNo}, updated_at = NOW() " +
            "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int markPaid(@Param("outTradeNo") String outTradeNo, @Param("tradeNo") String tradeNo);

    /** 关闭超时未付订单（本地不强制执行，保留清理能力） */
    @Update("UPDATE t_wallet_recharge_order " +
            "SET status = 'CLOSED', updated_at = NOW() " +
            "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int markClosed(@Param("outTradeNo") String outTradeNo);
}
