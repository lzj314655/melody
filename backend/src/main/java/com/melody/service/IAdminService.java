package com.melody.service;

import com.melody.entity.TAdmin;

import java.util.List;

/**
 * 后台管理员 Service（含 RBAC 权限加载）
 */
public interface IAdminService {

    /** 登录，返回 JWT */
    String login(String username, String password);

    TAdmin getById(Long adminId);

    List<TAdmin> list();

    TAdmin create(TAdmin admin);

    void delete(Long adminId);

    void resetPassword(Long adminId, String newPassword);
}