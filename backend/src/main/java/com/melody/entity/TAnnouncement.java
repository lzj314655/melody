package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 平台公告表 (F-A-063)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_announcement")
public class TAnnouncement extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告内容 */
    private String content;

    /** 状态 1发布 0草稿 */
    private Integer status;

    /** 是否置顶 */
    private Integer isTop;

    /** 发布时间 */
    private LocalDateTime publishAt;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 发布人 */
    private String operator;
}