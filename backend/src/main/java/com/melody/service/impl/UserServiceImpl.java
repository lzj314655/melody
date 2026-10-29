package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.exception.BizException;
import com.melody.common.JwtUtil;
import com.melody.common.ResultCode;
import com.melody.entity.TPointLog;
import com.melody.entity.TUser;
import com.melody.entity.TWalletAccount;
import com.melody.mapper.TPointLogMapper;
import com.melody.mapper.TUserMapper;
import com.melody.mapper.TWalletAccountMapper;
import com.melody.service.ICaptchaService;
import com.melody.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 用户业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final TUserMapper userMapper;
    private final TWalletAccountMapper walletMapper;
    private final TPointLogMapper pointLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ICaptchaService captchaService;

    /** PRD §7.3: 注册礼包 +100 积分 */
    private static final int REGISTER_BONUS_POINTS = 100;

    @Override
    @Transactional
    public TUser register(String phone, String password, String captchaId, String captchaCode) {
        // 1) 校验图片验证码
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片验证码错误或已过期");
        }
        if (phone == null || password == null || password.length() < 6) {
            throw new BizException(ResultCode.BAD_REQUEST, "手机号或密码不合法（密码至少 6 位）");
        }
        // 唯一性校验
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<TUser>()
                .eq(TUser::getPhone, phone));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 建用户
        TUser u = new TUser();
        u.setPhone(phone);
        u.setUsername(phone);
        u.setPassword(passwordEncoder.encode(password));
        u.setNickname("麦乐迪用户" + phone.substring(phone.length() - 4));
        u.setRankCode("V0");
        u.setPoints(REGISTER_BONUS_POINTS);
        u.setGrowthValue(0);
        u.setStatus(1);
        u.setJoinDate(LocalDateTime.now());
        u.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(u);

        // 建钱包（PRD §7.6 默认余额 0）
        TWalletAccount wallet = new TWalletAccount();
        wallet.setUserId(u.getId());
        wallet.setBalance(java.math.BigDecimal.ZERO);
        wallet.setFrozen(java.math.BigDecimal.ZERO);
        walletMapper.insert(wallet);

        // 写积分流水
        TPointLog pl = new TPointLog();
        pl.setUserId(u.getId());
        pl.setChangeAmount(REGISTER_BONUS_POINTS);
        pl.setBalanceAfter(REGISTER_BONUS_POINTS);
        pl.setType("REGISTER");
        pl.setReason("注册礼包");
        pointLogMapper.insert(pl);

        log.info("[User] register phone={} id={}", phone, u.getId());
        return u;
    }

    @Override
    public String login(String phone, String password, String captchaId, String captchaCode) {
        // 1) 校验图片验证码(失败的也要消耗掉,防止爆破)
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片验证码错误或已过期");
        }
        TUser u = userMapper.selectOne(new LambdaQueryWrapper<TUser>()
                .eq(TUser::getPhone, phone));
        if (u == null) {
            throw new BizException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(password, u.getPassword())) {
            throw new BizException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(ResultCode.USER_BLACKLISTED);
        }
        // 更新最后登录时间（轻量，不开事务）
        u.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(u);

        // JWT：前台用户不带权限，permissions 为空
        return jwtUtil.generate(
                String.valueOf(u.getId()),
                u.getUsername(),
                "user",
                Collections.emptyList());
    }

    @Override
    @Transactional
    public TUser updateProfile(Long userId, TUser patch) {
        TUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        // 白名单字段：仅允许修改这些
        u.setNickname(patch.getNickname());
        u.setAvatar(patch.getAvatar());
        u.setGender(patch.getGender());
        u.setBirthday(patch.getBirthday());
        userMapper.updateById(u);
        return u;
    }

    @Override
    public TUser getById(Long userId) {
        TUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        // 不返回密码
        u.setPassword(null);
        return u;
    }

    @Override
    public TUser getByPhone(String phone) {
        TUser u = userMapper.selectOne(new LambdaQueryWrapper<TUser>()
                .eq(TUser::getPhone, phone));
        if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        u.setPassword(null);
        return u;
    }
}