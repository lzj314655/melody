package com.melody.exception;

/**
 * 未登录 / Token 异常
 * 由 GlobalExceptionHandler 转为 401 + R.UNAUTHORIZED
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String msg) {
        super(msg);
    }
    public UnauthorizedException() {
        super("请先登录");
    }
}