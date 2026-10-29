package com.melody.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.dto.AdminDTOs;
import com.melody.entity.TAdmin;
import com.melody.entity.TAdminRole;
import com.melody.entity.TRole;
import com.melody.mapper.TAdminRoleMapper;
import com.melody.mapper.TRoleMapper;
import com.melody.service.IAdminService;
import com.melody.service.impl.OperationLogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台系统管理（PRD §8.2 /api/admin/system，F-A-060 ~ F-A-066）
 */
@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSystemController {

    private final IAdminService adminService;
    private final TAdminRoleMapper adminRoleMapper;
    private final TRoleMapper roleMapper;
    private final OperationLogServiceImpl opLog;

    /** F-A-060 管理员列表（带角色名） */
    @GetMapping("/admin/list")
    public R<List<Map<String, Object>>> list() {
        List<TAdmin> admins = adminService.list();
        // 批量取所有 admin-role 映射 + role 名称
        List<TAdminRole> ars = adminRoleMapper.selectList(null);
        List<Long> roleIds = ars.stream().map(TAdminRole::getRoleId).distinct().collect(Collectors.toList());
        Map<Long, TRole> roleMap = new HashMap<>();
        if (!roleIds.isEmpty()) {
            roleMapper.selectBatchIds(roleIds).forEach(r -> roleMap.put(r.getId(), r));
        }
        Map<Long, List<TRole>> adminRoles = new HashMap<>();
        ars.forEach(ar -> {
            TRole r = roleMap.get(ar.getRoleId());
            if (r != null) {
                adminRoles.computeIfAbsent(ar.getAdminId(), k -> new ArrayList<>()).add(r);
            }
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (TAdmin a : admins) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("username", a.getUsername());
            m.put("email", a.getEmail());
            m.put("phone", a.getPhone());
            m.put("displayName", a.getDisplayName());
            m.put("status", a.getStatus());
            m.put("lastLoginAt", a.getLastLoginAt());
            m.put("createdAt", a.getCreatedAt());
            List<TRole> roles = adminRoles.getOrDefault(a.getId(), Collections.emptyList());
            List<String> roleCodes = roles.stream().map(TRole::getCode).collect(Collectors.toList());
            List<String> roleNames = roles.stream().map(TRole::getName).collect(Collectors.toList());
            m.put("roleCodes", roleCodes);
            m.put("roleNames", roleNames);
            // 兼容旧前端：给个 role 字段 = 第一个角色名（中文）
            m.put("role", roleNames.isEmpty() ? "运营管理员" : roleNames.get(0));
            out.add(m);
        }
        return R.ok(out);
    }

    @PostMapping("/admin/create")
    public R<TAdmin> create(@RequestBody AdminDTOs.AdminCreateRequest req) {
        TAdmin a = new TAdmin();
        a.setUsername(req.getUsername());
        a.setPassword(req.getPassword());
        a.setEmail(req.getEmail());
        a.setPhone(req.getPhone());
        a.setDisplayName(req.getDisplayName());
        TAdmin created = adminService.create(a);
        // 绑定默认 OPERATOR 角色
        TRole opRole = roleMapper.selectOne(
                new LambdaQueryWrapper<TRole>().eq(TRole::getCode, "ROLE_OPERATOR"));
        if (opRole != null) {
            TAdminRole ar = new TAdminRole();
            ar.setAdminId(created.getId());
            ar.setRoleId(opRole.getId());
            adminRoleMapper.insert(ar);
        }
        opLog.log("admin", "创建管理员账号「" + created.getUsername() + "」");
        return R.ok(created);
    }

    @DeleteMapping("/admin/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        // 清理角色绑定
        adminRoleMapper.delete(new LambdaQueryWrapper<TAdminRole>().eq(TAdminRole::getAdminId, id));
        opLog.log("admin", "删除管理员 id=" + id);
        return R.<Void>okMsg("已删除");
    }

    @PostMapping("/admin/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id,
                                 @RequestBody AdminDTOs.ResetPasswordRequest req) {
        adminService.resetPassword(id, req.getNewPassword());
        return R.<Void>okMsg("密码已重置");
    }

    @PostMapping("/data-reset")
    public R<Void> resetData() {
        return R.<Void>okMsg("演示数据已重置（mock）");
    }
}