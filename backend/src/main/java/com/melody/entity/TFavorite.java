package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户收藏表
 *
 * 注：t_favorite 表没有 updated_at 字段（schema.sql 中只有 created_at），
 *     用 @TableField(exist = false) 屏蔽 BaseEntity 的 updatedAt。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_favorite")
public class TFavorite extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 商品 ID */
    private Long productId;

    /** 收藏时间（覆盖 BaseEntity 的 createdAt 语义） */
    @TableField(value = "created_at")
    private LocalDateTime favoriteAt;

    /** t_favorite 表无 updated_at，BaseEntity 的字段不映射 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}