package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类表（多级树）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_category")
public class TCategory extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 父分类 ID，顶级为 NULL */
    private Long parentId;

    /** 分类图标 URL */
    private String icon;

    /** 排序权重，数字越大越靠前 */
    private Integer sort;

    /** 状态 1启用 0禁用 */
    private Integer status;
}