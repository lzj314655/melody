package com.melody.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    /* ---------- 通用 1xxx ---------- */
    BAD_REQUEST(1001, "请求参数不合法"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),
    FORBIDDEN(1003, "无权限访问"),
    NOT_FOUND(1004, "资源不存在"),

    /* ---------- 用户模块 2xxx ---------- */
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_PASSWORD_ERROR(2002, "用户名或密码错误"),
    USER_ALREADY_EXISTS(2003, "用户已存在"),
    USER_BLACKLISTED(2004, "账号已被冻结"),

    /* ---------- 商品模块 3xxx ---------- */
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_OFF_SHELF(3002, "商品已下架"),
    PRODUCT_OUT_OF_STOCK(3003, "商品库存不足"),
    SKU_NOT_FOUND(3004, "商品规格不存在"),

    /* ---------- 订单模块 4xxx ---------- */
    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_STATUS_INVALID(4002, "订单当前状态不支持该操作"),
    ORDER_REFUND_FAILED(4003, "退款申请失败"),
    CART_EMPTY(4004, "购物车为空"),

    /* ---------- 支付/钱包 5xxx ---------- */
    WALLET_BALANCE_INSUFFICIENT(5001, "钱包余额不足"),
    PAYMENT_FAILED(5002, "支付失败"),

    /* ---------- 推荐/数据 6xxx ---------- */
    RECOMMEND_FAILED(6001, "推荐服务异常"),

    /* ---------- 评价模块 6xxx ---------- */
    REVIEW_NOT_PURCHASED(6010, "仅购买过该商品的用户才能评价"),
    REVIEW_ALREADY_EXISTS(6011, "您已评价过该商品"),
    REVIEW_NOT_FOUND(6012, "评价不存在"),

    /* ---------- 系统 9xxx ---------- */
    INTERNAL_ERROR(9999, "服务器内部错误");

    private final Integer code;
    private final String msg;
}