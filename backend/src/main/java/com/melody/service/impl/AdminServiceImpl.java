package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.exception.BizException;
import com.melody.common.JwtUtil;
import com.melody.common.ResultCode;
import com.melody.entity.TAdmin;
import com.melody.entity.TAdminRole;
import com.melody.entity.TPermission;
import com.melody.entity.TRole;
import com.melody.entity.TRolePermission;
import com.melody.mapper.TAdminMapper;
import com.melody.mapper.TAdminRoleMapper;
import com.melody.mapper.TPermissionMapper;
import com.melody.mapper.TRoleMapper;
import com.melody.mapper.TRolePermissionMapper;
import com.melody.service.IAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements IAdminService {

    private final TAdminMapper adminMapper;
    private final TAdminRoleMapper adminRoleMapper;
    private final TRoleMapper roleMapper;
    private final TRolePermissionMapper rolePermMapper;
    private final TPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public String login(String username, String password) {
        TAdmin a = adminMapper.selectOne(new LambdaQueryWrapper<TAdmin>()
                .eq(TAdmin::getUsername, username));
        if (a == null) throw new BizException(ResultCode.USER_PASSWORD_ERROR);
        if (!passwordEncoder.matches(password, a.getPassword())) {
            throw new BizException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (a.getStatus() != null && a.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已停用");
        }
        a.setLastLoginAt(java.time.LocalDateTime.now());
        adminMapper.updateById(a);

        // 加载权限 code 列表写进 JWT
        List<String> perms = loadPermissions(a.getId());
        return jwtUtil.generate(String.valueOf(a.getId()), a.getUsername(), "admin", perms);
    }

    @Override
    public TAdmin getById(Long adminId) {
        TAdmin a = adminMapper.selectById(adminId);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND, "管理员不存在");
        a.setPassword(null);
        return a;
    }

    @Override
    public List<TAdmin> list() {
        List<TAdmin> list = adminMapper.selectList(null);
        list.forEach(a -> a.setPassword(null));
        return list;
    }

    @Override
    @Transactional
    public TAdmin create(TAdmin admin) {
        // 重名校验
        Long cnt = adminMapper.selectCount(new LambdaQueryWrapper<TAdmin>()
                .eq(TAdmin::getUsername, admin.getUsername()));
        if (cnt != null && cnt > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "用户名已存在");
        }
        admin.setId(null);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        if (admin.getStatus() == null) admin.setStatus(1);
        adminMapper.insert(admin);
        return admin;
    }

    @Override
    @Transactional
    public void delete(Long adminId) {
        TAdmin a = adminMapper.selectById(adminId);
        if (a != null && "admin".equals(a.getUsername())) {
            throw new BizException(ResultCode.FORBIDDEN, "不能删除内置超级管理员");
        }
        adminMapper.deleteById(adminId);
    }

    @Override
    @Transactional
    public void resetPassword(Long adminId, String newPassword) {
        TAdmin a = adminMapper.selectById(adminId);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND, "管理员不存在");
        a.setPassword(passwordEncoder.encode(newPassword));
        adminMapper.updateById(a);
    }

    /** 加载管理员的全部权限 code（admin → roles → permissions） */
    private List<String> loadPermissions(Long adminId) {
        List<TAdminRole> ars = adminRoleMapper.selectList(new LambdaQueryWrapper<TAdminRole>()
                .eq(TAdminRole::getAdminId, adminId));
        if (ars.isEmpty()) return Collections.emptyList();
        List<Long> roleIds = ars.stream().map(TAdminRole::getRoleId).toList();
        List<TRole> roles = roleMapper.selectBatchIds(roleIds);
        Set<String> activeRoles = roles.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(TRole::getCode)
                .collect(Collectors.toSet());
        // 同时返回 role code 与 permission code（@PreAuthorize 既能 hasRole 又能 hasAuthority）
        List<TRolePermission> rps = rolePermMapper.selectList(new LambdaQueryWrapper<TRolePermission>()
                .in(TRolePermission::getRoleId, roleIds));
        if (rps.isEmpty()) return List.copyOf(activeRoles);
        List<Long> permIds = rps.stream().map(TRolePermission::getPermissionId).toList();
        List<TPermission> perms = permissionMapper.selectBatchIds(permIds);
        List<String> codes = perms.stream()
                .filter(p -> activeRoles.contains("ROLE_SUPER_ADMIN") || true) // 简化：所有权限都加载
                .map(TPermission::getCode)
                .toList();
        // 同时把 role code 也带上，便于 hasRole() 判定
        return java.util.stream.Stream.concat(activeRoles.stream(), codes.stream())
                .distinct().collect(Collectors.toList());
    }
}