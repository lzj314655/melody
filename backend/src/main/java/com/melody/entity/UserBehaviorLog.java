package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户行为日志（推荐系统数据源，PRD §4.3.3）
 *
 * 表名是 user_behavior_log（无 t_ 前缀），extra 字段是 JSON。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "user_behavior_log", autoResultMap = true)
public class UserBehaviorLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（游客为 NULL） */
    private Long userId;

    /** 商品 ID */
    private Long productId;

    /** 行为类型：VIEW / ADD_CART / FAVORITE / ORDER */
    private String behaviorType;

    /** 隐式反馈权重：浏览 1 / 加购 3 / 收藏 4 / 下单 5 */
    private Integer weight;

    /** 附加信息 JSON（spec/quantity/orderId 等） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /** 客户端 IP */
    private String clientIp;

    /** user_behavior_log 没有 updated_at，BaseEntity 字段不映射 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;

    /** 覆盖 createdAt 语义为事件发生时间 */
    @TableField(value = "created_at")
    private LocalDateTime occurredAt;
}