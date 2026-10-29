package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 首页轮播图表 (F-U-010)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_banner")
public class TBanner extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 轮播图标题 */
    private String title;

    /** 图片 URL */
    private String imageUrl;

    /** 点击跳转链接 */
    private String linkUrl;

    /** 排序，数字越小越靠前 */
    private Integer sort;

    /** 状态 1启用 0禁用 */
    private Integer status;

    /** 生效开始时间 */
    private LocalDateTime startDate;

    /** 生效结束时间 */
    private LocalDateTime endDate;
}