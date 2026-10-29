package com.melody.config;

import cn.hutool.core.util.StrUtil;
import com.melody.common.R;
import com.melody.common.ResultCode;
import com.melody.exception.BizException;
import com.melody.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：所有 controller 抛出的异常都走这里，统一返回 R 格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 自定义业务异常 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException ex) {
        log.warn("[BizException] code={} msg={}", ex.getCode(), ex.getMessage());
        return R.fail(ex.getCode(), ex.getMessage());
    }

    /** 参数校验异常（@Valid 失败） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ValidException] {}", msg);
        return R.fail(ResultCode.BAD_REQUEST.getCode(),
                StrUtil.isBlank(msg) ? ResultCode.BAD_REQUEST.getMsg() : msg);
    }

    /** @ModelAttribute 绑定异常 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** @RequestParam / @PathVariable 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** 未登录 / Token 过期 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<R<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ResultCode.UNAUTHORIZED.getCode(), ex.getMessage()));
    }

    /** Spring Security: 未登录 */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<R<Void>> handleAuthException(
            org.springframework.security.core.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ResultCode.UNAUTHORIZED.getCode(), "请先登录"));
    }

    /** Spring Security: 无权限 */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(ResultCode.FORBIDDEN.getCode(), "无权限访问该资源"));
    }

    /** 兜底：所有未捕获的运行时异常 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleAny(Exception ex) {
        log.error("[UnhandledException]", ex);
        return R.fail(ResultCode.INTERNAL_ERROR.getCode(),
                ResultCode.INTERNAL_ERROR.getMsg() + ": " + ex.getMessage());
    }
}