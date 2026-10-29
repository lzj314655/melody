package com.melody.exception;

import com.melody.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BizException 单元测试。
 */
@DisplayName("BizException 测试")
class BizExceptionTest {

    @Test
    @DisplayName("ctor(ResultCode) 继承 code/msg")
    void ctorResultCode() {
        BizException ex = new BizException(ResultCode.USER_NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
        assertThat(ex.getMessage()).isEqualTo(ResultCode.USER_NOT_FOUND.getMsg());
        assertThat(ex.getMessage()).isEqualTo(ResultCode.USER_NOT_FOUND.getMsg());
    }

    @Test
    @DisplayName("ctor(ResultCode, 自定义 msg) 覆盖 msg")
    void ctorResultCodeWithCustomMsg() {
        BizException ex = new BizException(ResultCode.BAD_REQUEST, "手机号格式错");
        assertThat(ex.getCode()).isEqualTo(1001);
        assertThat(ex.getMessage()).isEqualTo("手机号格式错");
    }

    @Test
    @DisplayName("ctor(code, msg) 自由指定")
    void ctorCodeAndMsg() {
        BizException ex = new BizException(9001, "自定义业务码");
        assertThat(ex.getCode()).isEqualTo(9001);
        assertThat(ex.getMessage()).isEqualTo("自定义业务码");
    }

    @Test
    @DisplayName("BizException 是 RuntimeException 子类")
    void isRuntimeException() {
        BizException ex = new BizException(ResultCode.FAIL);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
