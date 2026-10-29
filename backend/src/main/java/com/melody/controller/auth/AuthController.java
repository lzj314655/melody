package com.melody.controller.auth;

import com.melody.common.JwtUtil;
import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.AuthDTOs;
import com.melody.entity.TUser;
import com.melody.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 前台认证接口（PRD §8.1 /api/auth）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;
    private final JwtUtil jwtUtil;

    /** F-U-003 账号密码登录 */
    @PostMapping("/login")
    public R<AuthDTOs.LoginResponse> login(@Valid @RequestBody AuthDTOs.LoginRequest req) {
        String token = userService.login(req.getPhone(), req.getPassword(), req.getCaptchaId(), req.getCaptchaCode());
        AuthDTOs.LoginResponse res = new AuthDTOs.LoginResponse();
        // 从 phone 查 userId（用 getByPhone 走 mapper，避免全表扫描）
        TUser full = userService.getByPhone(req.getPhone());
        res.setToken(token);
        res.setUserId(full.getId());
        res.setUsername(full.getUsername());
        res.setNickname(full.getNickname());
        res.setAvatar(full.getAvatar());
        return R.ok("登录成功", res);
    }

    /** F-U-001 注册 */
    @PostMapping("/register")
    public R<AuthDTOs.LoginResponse> register(@Valid @RequestBody AuthDTOs.RegisterRequest req) {
        TUser u = userService.register(req.getPhone(), req.getPassword(), req.getCaptchaId(), req.getCaptchaCode());
        // 注册成功后直接生成 token,避免让前端再走一次登录流程(也就避免再要一次验证码)
        String token = jwtUtil.generate(String.valueOf(u.getId()), u.getUsername(), "user", java.util.Collections.emptyList());
        AuthDTOs.LoginResponse res = new AuthDTOs.LoginResponse();
        res.setToken(token);
        res.setUserId(u.getId());
        res.setUsername(u.getUsername());
        res.setNickname(u.getNickname());
        res.setAvatar(u.getAvatar());
        return R.ok("注册成功", res);
    }
}