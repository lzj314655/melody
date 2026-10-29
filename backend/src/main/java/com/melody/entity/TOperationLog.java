package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台操作日志表 (F-C-007)
 *
 * 无 updated_at，BaseEntity 不继承。
 */
@Data
@TableName("t_operation_log")
public class TOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作员（admin / system） */
    private String operator;

    /** 事件描述 */
    private String event;

    /** 所属模块 */
    private String module;

    /** 操作 IP */
    private String ip;

    /** 浏览器 UA */
    private String userAgent;

    /** 创建时间 */
    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}