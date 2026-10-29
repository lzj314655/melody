package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评价表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product_review")
public class TProductReview extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** 评价用户 ID */
    private Long userId;

    /** 关联订单 ID（可空） */
    private Long orderId;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 是否匿名 1是 0否 */
    private Integer isAnonymous;

    /** 是否隐藏 1是 0否（后台审核） */
    private Integer isHidden;
}