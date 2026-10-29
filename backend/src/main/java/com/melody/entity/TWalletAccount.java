package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 用户虚拟钱包账户 (PRD §5.5 F-C-003)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wallet_account")
public class TWalletAccount extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID（一对一） */
    private Long userId;

    /** 可用余额（元） */
    private BigDecimal balance;

    /** 冻结金额（提现/退款中） */
    private BigDecimal frozen;

    /** 支付密码（BCrypt，可空） */
    private String password;
}