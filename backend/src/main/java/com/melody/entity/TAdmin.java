package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台管理员账号表 (PRD §3.1 ADMIN/OPERATOR)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admin")
public class TAdmin extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** BCrypt 加密密码 */
    private String password;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 显示名 */
    private String displayName;

    /** 状态 1启用 0禁用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP */
    private String lastLoginIp;
}