package com.melody.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.entity.TUser;
import com.melody.entity.TUserAddress;
import com.melody.mapper.TUserAddressMapper;
import com.melody.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台用户接口（PRD §8.1 /api/user + /api/address）
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final TUserAddressMapper addressMapper;

    /** F-U-007 / F-U-050 获取个人信息 */
    @GetMapping("/api/user")
    public R<TUser> getProfile() {
        return R.ok(userService.getById(SecurityUtils.currentId()));
    }

    /** F-U-007 修改个人资料 */
    @PutMapping("/api/user")
    public R<TUser> updateProfile(@RequestBody TUser patch) {
        return R.ok(userService.updateProfile(SecurityUtils.currentId(), patch));
    }

    /** F-U-051 收货地址列表 */
    @GetMapping("/api/address")
    public R<List<TUserAddress>> listAddresses() {
        List<TUserAddress> list = addressMapper.selectList(new LambdaQueryWrapper<TUserAddress>()
                .eq(TUserAddress::getUserId, SecurityUtils.currentId())
                .orderByDesc(TUserAddress::getIsDefault));
        return R.ok(list);
    }

    /** F-U-051 收货地址 CRUD（演示：复用 UserAddress 实体直接 upsert） */
    @PostMapping("/api/address")
    @Transactional
    public R<TUserAddress> addAddress(@RequestBody TUserAddress addr) {
        Long userId = SecurityUtils.currentId();
        addr.setId(null);
        addr.setUserId(userId);
        if (addr.getIsDefault() == null) {
            addr.setIsDefault(0);
        }

        // 首条地址自动设为默认，避免用户没有默认地址
        boolean firstAddress = addressMapper.selectCount(new LambdaQueryWrapper<TUserAddress>()
                .eq(TUserAddress::getUserId, userId)) == 0;
        if (firstAddress) {
            addr.setIsDefault(1);
        }

        // 与 updateAddress 一致：新增默认地址时清掉同用户其他地址的默认标志
        if (addr.getIsDefault() == 1) {
            clearOtherDefaults(userId, null);
        }

        addressMapper.insert(addr);
        return R.ok(addr);
    }

    @PutMapping("/api/address/{id}")
    @Transactional
    public R<TUserAddress> updateAddress(@PathVariable Long id, @RequestBody TUserAddress addr) {
        Long userId = SecurityUtils.currentId();
        addr.setId(id);
        addr.setUserId(userId);

        // F-U-051 设为默认时，把同用户其他地址先清掉默认标志，避免出现多条 isDefault=1
        if (addr.getIsDefault() != null && addr.getIsDefault() == 1) {
            clearOtherDefaults(userId, id);
        }

        addressMapper.updateById(addr);
        return R.ok(addr);
    }

    /** 把同用户除 keepId 之外的地址默认标志清零；keepId 为 null 表示全部清零 */
    private void clearOtherDefaults(Long userId, Long keepId) {
        LambdaUpdateWrapper<TUserAddress> wrapper = new LambdaUpdateWrapper<TUserAddress>()
                .set(TUserAddress::getIsDefault, 0)
                .eq(TUserAddress::getUserId, userId)
                .eq(TUserAddress::getIsDefault, 1);
        if (keepId != null) {
            wrapper.ne(TUserAddress::getId, keepId);
        }
        addressMapper.update(null, wrapper);
    }

    @DeleteMapping("/api/address/{id}")
    public R<Void> deleteAddress(@PathVariable Long id) {
        addressMapper.deleteById(id);
        return R.ok();
    }
}