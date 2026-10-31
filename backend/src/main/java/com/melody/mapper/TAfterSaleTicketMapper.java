package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TAfterSaleTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TAfterSaleTicketMapper extends BaseMapper<TAfterSaleTicket> {

    /**
     * 原子流转工单状态，避免并发审核重复退款。
     *
     * @return 1=流转成功，0=当前状态已变化
     */
    @Update("UPDATE t_after_sale_ticket " +
            "SET status = #{toStatus}, decision = #{decision}, decision_reason = #{reason} " +
            "WHERE id = #{id} AND status = #{fromStatus}")
    int markStatus(@Param("id") Long id,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("decision") String decision,
                   @Param("reason") String reason);

    @Update("UPDATE t_after_sale_ticket SET reply = #{reply} WHERE id = #{id}")
    int updateReply(@Param("id") Long id, @Param("reply") String reply);

    @Update("UPDATE t_after_sale_ticket SET assignee = #{assignee} WHERE id = #{id}")
    int updateAssignee(@Param("id") Long id, @Param("assignee") String assignee);
}
