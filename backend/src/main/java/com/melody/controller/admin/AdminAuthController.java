package com.melody.controller.admin;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.AdminDTOs;
import com.melody.entity.TAdmin;
import com.melody.service.IAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台管理员认证（PRD §8.2 /api/admin/auth）
 *
 * 登录接口已配 permitAll，登录后由 JwtAuthFilter 解析 token。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final IAdminService adminService;

    /** 登录 */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody AdminDTOs.AdminLoginRequest req) {
        String token = adminService.login(req.getUsername(), req.getPassword());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        // 取回 admin 信息
        TAdmin a = adminService.list().stream()
                .filter(x -> x.getUsername().equals(req.getUsername())).findFirst().orElseThrow();
        a.setPassword(null);
        data.put("admin", a);
        return R.ok("登录成功", data);
    }

    /** 当前管理员信息 */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    public R<TAdmin> me() {
        return R.ok(adminService.getById(SecurityUtils.currentId()));
    }

    /** 退出登录（前端清 token 即可，这里返回成功） */
    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    public R<Void> logout() {
        return R.<Void>okMsg("已退出");
    }
}