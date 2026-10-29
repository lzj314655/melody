package com.melody.service;

import com.melody.entity.TUser;

/**
 * 前台用户 Service
 */
public interface IUserService {

    /** F-U-001 注册（图片验证码 captchaId + captchaCode） */
    TUser register(String phone, String password, String captchaId, String captchaCode);

    /** F-U-003 登录（账号密码登录 + 图片验证码） */
    String login(String phone, String password, String captchaId, String captchaCode);

    /** F-U-007 / F-U-050 修改个人资料 */
    TUser updateProfile(Long userId, TUser patch);

    /** 根据 ID 查询 */
    TUser getById(Long userId);

    /** 根据手机号查询（AuthController 登录后回查用） */
    TUser getByPhone(String phone);
}