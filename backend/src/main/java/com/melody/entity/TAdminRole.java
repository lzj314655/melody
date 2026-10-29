package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员-角色关联表
 *
 * 无 updated_at，BaseEntity 不继承，直接 @Data。
 */
@Data
@TableName("t_admin_role")
public class TAdminRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理员 ID */
    private Long adminId;

    /** 角色 ID */
    private Long roleId;

    /** 创建时间 */
    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}