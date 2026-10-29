package com.melody.controller;

import com.melody.common.R;
import com.melody.service.ICaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图片验证码接口(公开)
 *
 * GET /api/captcha         生成验证码,返回 {id, code} - code 仅调试期返回给前端画图
 * GET /api/captcha/{id}    取当前 code(供前端首次生成 SVG 时用,生产应只返 SVG)
 *
 * ⚠️ 毕设简化版:直接返明文 code,前端画 SVG。
 *    生产环境应该只返 SVG image/png 二进制,不让前端看到明文。
 */
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final ICaptchaService captchaService;

    @GetMapping
    public R<Map<String, String>> create() {
        String id = captchaService.create();
        String code = captchaService.getCode(id);
        return R.ok(Map.of("id", id, "code", code == null ? "" : code));
    }
}