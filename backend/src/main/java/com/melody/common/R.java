package com.melody.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应封装
 *
 * @param <T> 数据类型
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private Integer code;
    /** 提示信息 */
    private String msg;
    /** 响应数据 */
    private T data;

    public R() {}

    public R(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    /** 仅返回成功消息（无数据），如 R.<Void>ok("操作成功") */
    public static <T> R<T> okMsg(String msg) {
        return new R<>(ResultCode.SUCCESS.getCode(), msg, null);
    }

    public static <T> R<T> fail() {
        return new R<>(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMsg(), null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(ResultCode.FAIL.getCode(), msg, null);
    }

    public static <T> R<T> fail(ResultCode rc) {
        return new R<>(rc.getCode(), rc.getMsg(), null);
    }

    public static <T> R<T> fail(Integer code, String msg) {
        return new R<>(code, msg, null);
    }
}