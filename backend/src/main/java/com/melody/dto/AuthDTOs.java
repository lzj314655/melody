package com.melody.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证相关 DTO
 */
public class AuthDTOs {

    @Data
    public static class LoginRequest {
        @NotBlank
        private String phone;
        @NotBlank
        @Size(min = 6)
        private String password;
        /** 图片验证码 id(从 GET /api/captcha 获取) */
        @NotBlank
        private String captchaId;
        /** 图片验证码 4 位字符 */
        @NotBlank
        private String captchaCode;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank
        private String phone;
        @NotBlank
        @Size(min = 6, max = 32)
        private String password;
        /** 图片验证码 id(从 GET /api/captcha 获取) */
        @NotBlank
        private String captchaId;
        /** 图片验证码 4 位字符 */
        @NotBlank
        private String captchaCode;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
    }
}