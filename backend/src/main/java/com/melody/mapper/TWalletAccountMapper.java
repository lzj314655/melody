package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TWalletAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * TWalletAccount 表 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TWalletAccountMapper extends BaseMapper<TWalletAccount> {

    /**
     * 原子增减余额(带下限检查)
     *
     * UPDATE t_wallet_account
     * SET balance = balance + #{delta}
     * WHERE user_id = #{userId}
     *   AND (#{minRequired} IS NULL OR balance + #{delta} >= #{minRequired})
     *
     * @return 影响的行数(0 = 下限不足/记录不存在;1 = 成功)
     */
    @Update("UPDATE t_wallet_account " +
            "SET balance = balance + #{delta} " +
            "WHERE user_id = #{userId} " +
            "  AND (#{minRequired} IS NULL OR balance + #{delta} >= #{minRequired})")
    int atomicAddBalance(@Param("userId") Long userId,
                         @Param("delta") BigDecimal delta,
                         @Param("minRequired") BigDecimal minRequired);
}