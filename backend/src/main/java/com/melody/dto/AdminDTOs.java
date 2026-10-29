package com.melody.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台管理 DTO
 */
public class AdminDTOs {

    @Data
    public static class AdminLoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class AdminCreateRequest {
        @NotBlank
        @Size(min = 3, max = 32)
        private String username;
        @NotBlank
        @Size(min = 6, max = 32)
        private String password;
        @Email
        private String email;
        private String phone;
        private String displayName;
        /** 角色 code，如 ROLE_OPERATOR */
        @NotBlank
        private String roleCode;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        @Size(min = 6, max = 32)
        private String newPassword;
    }

    @Data
    public static class BehaviorReportRequest {
        /** VIEW / ADD_CART / FAVORITE / ORDER */
        @NotBlank
        private String behaviorType;
        @NotNull
        private Long productId;
        /** 附加信息 JSON 字符串 */
        private String extra;
    }
}