package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-权限关联表（无 updated_at）
 */
@Data
@TableName("t_role_permission")
public class TRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID */
    private Long permissionId;

    /** 创建时间 */
    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}