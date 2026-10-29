package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品图片表（一对多）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product_image")
public class TProductImage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商品 ID */
    private Long productId;

    /** 图片 URL */
    private String url;

    /** 排序，数字越小越靠前 */
    private Integer sort;
}