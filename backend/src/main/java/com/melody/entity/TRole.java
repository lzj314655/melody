package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台角色表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_role")
public class TRole extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色代码（如 ROLE_SUPER_ADMIN） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 状态 1启用 0禁用 */
    private Integer status;
}