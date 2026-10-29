package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品规格/SKU 表（PRD §6.1.1 V1.1 补充）
 *
 * 字段命名沿用 §6.1.1 定义（spec_name / sku_code / stock 等），
 * 主键统一命名为 id（PRD §6.1.1 中称 sku_id），与所有表一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product_sku")
public class TProductSku extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商品 ID */
    private Long productId;

    /** 规格名（如"麻辣味/60g"） */
    private String specName;

    /** SKU 编码 */
    private String skuCode;

    /** 该 SKU 的售价 */
    private BigDecimal price;

    /** 该 SKU 的库存 */
    private Integer stock;

    /** 该 SKU 对应图片（默认沿用商品主图） */
    private String image;

    /** 该 SKU 销量（冗余字段） */
    private Integer sales;

    /** 上下架状态 0下架 1上架 */
    private Integer status;
}