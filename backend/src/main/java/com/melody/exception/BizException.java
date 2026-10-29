package com.melody.exception;

import com.melody.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常：service 层抛出，由 GlobalExceptionHandler 捕获并返回 R
 *
 * 使用示例：throw new BizException(ResultCode.WALLET_BALANCE_INSUFFICIENT);
 *           throw new BizException(9999, "自定义错误");
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(ResultCode rc) {
        super(rc.getMsg());
        this.code = rc.getCode();
    }

    public BizException(ResultCode rc, String msg) {
        super(msg);
        this.code = rc.getCode();
    }

    public BizException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }
}