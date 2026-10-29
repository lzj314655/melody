package com.melody.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R 统一响应封装测试。
 */
@DisplayName("R 响应封装测试")
class RTest {

    @Test
    @DisplayName("ok() 默认带 SUCCESS code=200")
    void okDefault() {
        R<String> r = R.ok();
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMsg()).isEqualTo("操作成功");
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("ok(T) 携带 data")
    void okWithData() {
        R<String> r = R.ok("payload");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("payload");
    }

    @Test
    @DisplayName("ok(msg, data) 自定义消息")
    void okWithMsgAndData() {
        R<Integer> r = R.ok("自定义消息", 42);
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMsg()).isEqualTo("自定义消息");
        assertThat(r.getData()).isEqualTo(42);
    }

    @Test
    @DisplayName("okMsg 只带消息无数据")
    void okMsgOnly() {
        R<Void> r = R.okMsg("操作成功");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMsg()).isEqualTo("操作成功");
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("fail() 默认 FAIL 500")
    void failDefault() {
        R<Void> r = R.fail();
        assertThat(r.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("fail(msg) 自定义错误消息")
    void failWithMsg() {
        R<Void> r = R.fail("业务异常");
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).isEqualTo("业务异常");
    }

    @Test
    @DisplayName("fail(ResultCode) 使用枚举 code")
    void failWithResultCode() {
        R<Void> r = R.fail(ResultCode.USER_NOT_FOUND);
        assertThat(r.getCode()).isEqualTo(2001);
        assertThat(r.getMsg()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("fail(code, msg) 自由指定")
    void failWithCodeAndMsg() {
        R<Void> r = R.fail(9001, "自定义业务码");
        assertThat(r.getCode()).isEqualTo(9001);
        assertThat(r.getMsg()).isEqualTo("自定义业务码");
    }
}
