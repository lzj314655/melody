package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.JwtUtil;
import com.melody.common.ResultCode;
import com.melody.entity.TAdmin;
import com.melody.entity.TAdminRole;
import com.melody.entity.TPermission;
import com.melody.entity.TRole;
import com.melody.entity.TRolePermission;
import com.melody.exception.BizException;
import com.melody.mapper.TAdminMapper;
import com.melody.mapper.TAdminRoleMapper;
import com.melody.mapper.TPermissionMapper;
import com.melody.mapper.TRoleMapper;
import com.melody.mapper.TRolePermissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminServiceImpl 单元测试：login / getById / list / create / delete / resetPassword。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceImpl 单元测试")
class AdminServiceImplTest {

    @Mock private TAdminMapper adminMapper;
    @Mock private TAdminRoleMapper adminRoleMapper;
    @Mock private TRoleMapper roleMapper;
    @Mock private TRolePermissionMapper rolePermMapper;
    @Mock private TPermissionMapper permissionMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AdminServiceImpl adminService;

    private TAdmin admin(Long id, String username, int status) {
        TAdmin a = new TAdmin();
        a.setId(id);
        a.setUsername(username);
        a.setPassword("$bcrypt$");
        a.setStatus(status);
        return a;
    }

    // ============ login ============

    @Test
    @DisplayName("login 成功 → JWT 含 perms")
    void login_success() {
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin(1L, "root", 1));
        when(passwordEncoder.matches("123456", "$bcrypt$")).thenReturn(true);
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(makeAdminRole(1L, 10L)));
        TRole role = new TRole();
        role.setId(10L);
        role.setCode("ROLE_SUPER_ADMIN");
        role.setStatus(1);
        when(roleMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(role));
        TRolePermission rp = new TRolePermission();
        rp.setRoleId(10L);
        rp.setPermissionId(100L);
        when(rolePermMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rp));
        TPermission perm = new TPermission();
        perm.setId(100L);
        perm.setCode("dashboard:view");
        when(permissionMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(perm));
        when(jwtUtil.generate(anyString(), anyString(), anyString(), any())).thenReturn("mock.admin.token");

        String token = adminService.login("root", "123456");

        assertThat(token).isEqualTo("mock.admin.token");
        verify(jwtUtil).generate(anyString(), anyString(), eq("admin"), any());
        verify(adminMapper, times(1)).updateById(any());
    }

    private TAdminRole makeAdminRole(Long adminId, Long roleId) {
        TAdminRole ar = new TAdminRole();
        ar.setAdminId(adminId);
        ar.setRoleId(roleId);
        return ar;
    }

    @Test
    @DisplayName("login 用户不存在 → USER_PASSWORD_ERROR")
    void login_userNotFound() {
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> adminService.login("ghost", "x"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_PASSWORD_ERROR.getCode()));
    }

    @Test
    @DisplayName("login 密码错 → USER_PASSWORD_ERROR")
    void login_wrongPassword() {
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin(1L, "root", 1));
        when(passwordEncoder.matches("x", "$bcrypt$")).thenReturn(false);
        assertThatThrownBy(() -> adminService.login("root", "x"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("login 停用账号 → FORBIDDEN")
    void login_disabled() {
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin(1L, "root", 0));
        when(passwordEncoder.matches("x", "$bcrypt$")).thenReturn(true);
        assertThatThrownBy(() -> adminService.login("root", "x"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    // ============ getById / list ============

    @Test
    @DisplayName("getById 返回时清空密码")
    void getById_masksPassword() {
        when(adminMapper.selectById(1L)).thenReturn(admin(1L, "root", 1));
        TAdmin result = adminService.getById(1L);
        assertThat(result.getPassword()).isNull();
    }

    @Test
    @DisplayName("getById 不存在 → NOT_FOUND")
    void getById_missing() {
        when(adminMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> adminService.getById(99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("list 清空所有密码")
    void list_masksAllPasswords() {
        TAdmin a1 = admin(1L, "a", 1);
        TAdmin a2 = admin(2L, "b", 1);
        when(adminMapper.selectList(null)).thenReturn(List.of(a1, a2));

        List<TAdmin> result = adminService.list();
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(a -> assertThat(a.getPassword()).isNull());
    }

    // ============ create ============

    @Test
    @DisplayName("create 成功：BCrypt 加密 + 默认 status=1")
    void create_success() {
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("$bcrypt$encoded");
        when(adminMapper.insert(any(TAdmin.class))).thenAnswer(inv -> {
            TAdmin arg = inv.getArgument(0);
            arg.setId(99L);
            return 1;
        });

        TAdmin newAdmin = new TAdmin();
        newAdmin.setUsername("newadmin");
        newAdmin.setPassword("123456");

        TAdmin result = adminService.create(newAdmin);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getPassword()).isEqualTo("$bcrypt$encoded");
        assertThat(result.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("create 用户名已存在 → USER_ALREADY_EXISTS")
    void create_usernameExists() {
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        TAdmin a = new TAdmin();
        a.setUsername("dup");
        assertThatThrownBy(() -> adminService.create(a))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_ALREADY_EXISTS.getCode()));
    }

    // ============ delete ============

    @Test
    @DisplayName("delete 内置超级管理员 admin → FORBIDDEN")
    void delete_superAdmin_forbidden() {
        when(adminMapper.selectById(1L)).thenReturn(admin(1L, "admin", 1));
        assertThatThrownBy(() -> adminService.delete(1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("delete 普通管理员 → 成功")
    void delete_normal() {
        when(adminMapper.selectById(2L)).thenReturn(admin(2L, "ops", 1));

        adminService.delete(2L);

        verify(adminMapper).deleteById(2L);
    }

    @Test
    @DisplayName("delete 用户不存在 → 静默成功（无校验）")
    void delete_missing_noOp() {
        when(adminMapper.selectById(99L)).thenReturn(null);
        adminService.delete(99L);
        // 仍然调用 deleteById（业务实现如此）
        verify(adminMapper).deleteById(99L);
    }

    // ============ resetPassword ============

    @Test
    @DisplayName("resetPassword 加密新密码")
    void resetPassword_success() {
        TAdmin a = admin(1L, "root", 1);
        when(adminMapper.selectById(1L)).thenReturn(a);
        when(passwordEncoder.encode("newPass")).thenReturn("$bcrypt$new");

        adminService.resetPassword(1L, "newPass");

        assertThat(a.getPassword()).isEqualTo("$bcrypt$new");
        verify(adminMapper).updateById(a);
    }

    @Test
    @DisplayName("resetPassword 用户不存在 → NOT_FOUND")
    void resetPassword_missing() {
        when(adminMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> adminService.resetPassword(99L, "x"))
                .isInstanceOf(BizException.class);
    }
}
