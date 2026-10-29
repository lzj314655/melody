package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品主信息表
 *
 * tags 字段为 JSON 数组，用 JacksonTypeHandler 反序列化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_product", autoResultMap = true)
public class TProduct extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品名 */
    private String name;

    /** 所属分类 ID */
    private Long categoryId;

    /** 品牌 */
    private String brand;

    /** 默认售价 */
    private BigDecimal price;

    /** 市场价/划线价 */
    private BigDecimal marketPrice;

    /** 商品描述 */
    private String description;

    /** 封面图（首图） */
    private String coverImage;

    /** 默认库存（SKU 库存可独立） */
    private Integer inventory;

    /** 累计销量（冗余字段） */
    private Integer sales;

    /** 标签 JSON 数组，如 ["低糖健康","会员特惠"] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** 平均评分 0-5 */
    private BigDecimal rating;

    /** 上下架状态 1上架 0下架 */
    private Integer status;

    /** 产地 */
    private String origin;

    /** 保质期 */
    private String shelfLife;

    /** 净含量 */
    private String weight;

    /** 配料表 */
    private String ingredients;
}