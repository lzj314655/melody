package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TUserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * TUserCoupon 表 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TUserCouponMapper extends BaseMapper<TUserCoupon> {

    /**
     * 原子核销：只有「本人 + 可用」的券才能改成已用。
     * 并发用同一张券下两单时，数据库行锁保证只有一笔成功。
     *
     * @return 1=核销成功，0=券不存在/非本人/已用/已过期
     */
    @Update("UPDATE t_user_coupon " +
            "SET status = '已用', used_at = NOW(), used_order_id = #{orderId} " +
            "WHERE id = #{id} AND user_id = #{userId} AND status = '可用'")
    int markUsed(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("orderId") Long orderId);

    /** 取消未付款订单时，把该订单用掉的券退回「可用」。 */
    @Update("UPDATE t_user_coupon " +
            "SET status = '可用', used_at = NULL, used_order_id = NULL " +
            "WHERE user_id = #{userId} AND used_order_id = #{orderId} AND status = '已用'")
    int releaseByOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);
}
