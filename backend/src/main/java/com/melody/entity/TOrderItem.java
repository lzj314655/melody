package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单商品明细表
 *
 * sku_id 可空：演示数据不强求对齐 SKU 主键（schema.sql 已允许 NULL）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order_item")
public class TOrderItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属订单 ID */
    private Long orderId;

    /** 商品 ID */
    private Long productId;

    /** SKU ID（可空） */
    private Long skuId;

    /** 商品名（冗余快照） */
    private String productName;

    /** 商品图（冗余快照） */
    private String productImage;

    /** 规格名（冗余快照） */
    private String specName;

    /** 下单单价（冗余快照） */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计 = price × quantity */
    private BigDecimal subtotal;
}