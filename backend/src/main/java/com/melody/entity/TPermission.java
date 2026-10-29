package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台权限点表
 *
 * t_permission 表只有 created_at 没有 updated_at。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_permission")
public class TPermission extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限代码（如 product:read, order:ship） */
    private String code;

    /** 权限名 */
    private String name;

    /** 所属模块（product/order/marketing/...） */
    private String module;

    /** 描述 */
    private String description;

    /** t_permission 没有 updated_at，BaseEntity 字段不映射 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}