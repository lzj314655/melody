package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车明细表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_cart_item")
public class TCartItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 商品 ID */
    private Long productId;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    /** 加购时价格（快照） */
    private BigDecimal price;

    /** 规格名（冗余） */
    private String specName;

    /** 是否选中 (F-U-034) 1是 0否 */
    private Integer selected;
}