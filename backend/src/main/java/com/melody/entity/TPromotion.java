package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 满减活动表
 *
 * scope_value 是 JSON 数组（商品 ID 或分类 ID 列表），用 JacksonTypeHandler。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_promotion", autoResultMap = true)
public class TPromotion extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名称 */
    private String name;

    /** 类型：满减/折扣 */
    private String type;

    /** 门槛：满 X 元 */
    private BigDecimal minSpend;

    /** 优惠：减 Y 元 或 折扣率 */
    private BigDecimal discount;

    /** 范围：ALL 全场 / CATEGORY 分类 / PRODUCT 指定商品 */
    private String scopeType;

    /** 范围 ID 列表（按 scopeType 解释） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> scopeValue;

    /** 开始时间 */
    private LocalDateTime startDate;

    /** 结束时间 */
    private LocalDateTime endDate;

    /** 状态 1启用 0禁用 */
    private Integer status;

    /** 活动 banner 图 */
    private String bannerUrl;

    /** 活动说明 */
    private String description;
}