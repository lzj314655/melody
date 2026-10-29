package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 前台用户表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class TUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号（登录账号，唯一） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 用户名 */
    private String username;

    /** BCrypt 加密密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 性别 0未知 1男 2女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 会员等级 V0/V1/V2/V3 (PRD §7.5) */
    private String rankCode;

    /** 积分余额 (PRD §7.3) */
    private Integer points;

    /** 成长值 (PRD §7.5) */
    private Integer growthValue;

    /** 状态 1正常 0黑名单 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 注册时间 */
    private LocalDateTime joinDate;
}