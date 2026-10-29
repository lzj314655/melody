package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.JwtUtil;
import com.melody.common.ResultCode;
import com.melody.entity.TPointLog;
import com.melody.entity.TUser;
import com.melody.entity.TWalletAccount;
import com.melody.exception.BizException;
import com.melody.mapper.TPointLogMapper;
import com.melody.mapper.TUserMapper;
import com.melody.mapper.TWalletAccountMapper;
import com.melody.service.ICaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试：register / login / updateProfile / getById / getByPhone。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    @Mock private TUserMapper userMapper;
    @Mock private TWalletAccountMapper walletMapper;
    @Mock private TPointLogMapper pointLogMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private ICaptchaService captchaService;

    @InjectMocks
    private UserServiceImpl userService;

    private JwtUtil realJwt;

    @BeforeEach
    void setUp() {
        // 用真实的 JwtUtil，便于 login() 返回 token 后可以断言 token 字符串格式
        realJwt = new JwtUtil();
        ReflectionTestUtils.setField(realJwt, "secret", "test-secret-key-melody-shop-32bytes-please");
        ReflectionTestUtils.invokeMethod(realJwt, "init");
    }

    private TUser newUser(Long id, String phone) {
        TUser u = new TUser();
        u.setId(id);
        u.setPhone(phone);
        u.setUsername(phone);
        u.setPassword("$2a$encoded");
        u.setNickname("麦乐迪用户" + phone.substring(phone.length() - 4));
        u.setRankCode("V0");
        u.setPoints(0);
        u.setStatus(1);
        return u;
    }

    // ============ register ============

    @Test
    @DisplayName("register 成功：建用户 + 建钱包 + 写积分流水")
    void register_success() {
        when(captchaService.verify("cap-1", "ABCD")).thenReturn(true);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("$bcrypt$");

        TUser saved = newUser(null, "13800000001");
        when(userMapper.insert(any(TUser.class))).thenAnswer(inv -> {
            TUser arg = inv.getArgument(0);
            arg.setId(100L);
            return 1;
        });

        TUser result = userService.register("13800000001", "123456", "cap-1", "ABCD");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getNickname()).isEqualTo("麦乐迪用户0001");
        assertThat(result.getRankCode()).isEqualTo("V0");
        assertThat(result.getPoints()).isEqualTo(100); // REGISTER_BONUS_POINTS
        verify(walletMapper).insert(any(TWalletAccount.class));

        ArgumentCaptor<TPointLog> captor = ArgumentCaptor.forClass(TPointLog.class);
        verify(pointLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeAmount()).isEqualTo(100);
        assertThat(captor.getValue().getType()).isEqualTo("REGISTER");
    }

    @Test
    @DisplayName("register 验证码错误 → BAD_REQUEST")
    void register_wrongCaptcha() {
        when(captchaService.verify("cap-1", "XXXX")).thenReturn(false);

        assertThatThrownBy(() -> userService.register("13800000001", "123456", "cap-1", "XXXX"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));

        verify(userMapper, never()).insert(any(TUser.class));
    }

    @Test
    @DisplayName("register 密码不足 6 位 → BAD_REQUEST")
    void register_shortPassword() {
        when(captchaService.verify(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> userService.register("13800000001", "123", "cap-1", "ABCD"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("register 手机号已存在 → USER_ALREADY_EXISTS")
    void register_phoneExists() {
        when(captchaService.verify(any(), any())).thenReturn(true);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.register("13800000001", "123456", "cap-1", "ABCD"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_ALREADY_EXISTS.getCode()));
    }

    // ============ login ============

    @Test
    @DisplayName("login 成功 → 返回 JWT token")
    void login_success() {
        TUser u = newUser(7L, "13800000002");
        when(captchaService.verify(any(), any())).thenReturn(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("123456", u.getPassword())).thenReturn(true);
        when(jwtUtil.generate(eq("7"), eq("13800000002"), eq("user"), eq(Collections.emptyList())))
                .thenReturn("mock.jwt.token");

        String token = userService.login("13800000002", "123456", "cap-1", "ABCD");

        assertThat(token).isEqualTo("mock.jwt.token");
        verify(userMapper).updateById(any(TUser.class));
    }

    @Test
    @DisplayName("login 验证码错误")
    void login_wrongCaptcha() {
        when(captchaService.verify(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> userService.login("13800000001", "123456", "cap-1", "XXXX"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("login 用户不存在 → USER_PASSWORD_ERROR")
    void login_userNotFound() {
        when(captchaService.verify(any(), any())).thenReturn(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> userService.login("13800000001", "123456", "cap-1", "ABCD"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_PASSWORD_ERROR.getCode()));
    }

    @Test
    @DisplayName("login 密码错误 → USER_PASSWORD_ERROR")
    void login_wrongPassword() {
        TUser u = newUser(7L, "13800000002");
        when(captchaService.verify(any(), any())).thenReturn(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("wrong", u.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.login("13800000002", "wrong", "cap-1", "ABCD"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_PASSWORD_ERROR.getCode()));
    }

    @Test
    @DisplayName("login 黑名单用户 → USER_BLACKLISTED")
    void login_blacklisted() {
        TUser u = newUser(7L, "13800000002");
        u.setStatus(0);
        when(captchaService.verify(any(), any())).thenReturn(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.login("13800000002", "123456", "cap-1", "ABCD"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_BLACKLISTED.getCode()));
    }

    // ============ updateProfile ============

    @Test
    @DisplayName("updateProfile 白名单字段：仅 nickname/avatar/gender/birthday 可改")
    void updateProfile_whitelistOnly() {
        TUser existing = newUser(7L, "13800000002");
        existing.setPoints(500);
        when(userMapper.selectById(7L)).thenReturn(existing);

        TUser patch = new TUser();
        patch.setNickname("新昵称");
        patch.setAvatar("/new.jpg");
        patch.setGender(1);
        patch.setPoints(99999); // 不在白名单，应被忽略
        patch.setRankCode("V9"); // 不在白名单，应被忽略

        TUser result = userService.updateProfile(7L, patch);

        assertThat(result.getNickname()).isEqualTo("新昵称");
        assertThat(result.getAvatar()).isEqualTo("/new.jpg");
        assertThat(result.getGender()).isEqualTo(1);
        // 白名单外字段未被 patch 覆盖
        assertThat(result.getPoints()).isEqualTo(500);
        assertThat(result.getRankCode()).isEqualTo("V0");
        verify(userMapper).updateById(existing);
    }

    @Test
    @DisplayName("updateProfile 用户不存在 → USER_NOT_FOUND")
    void updateProfile_userMissing() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateProfile(999L, new TUser()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode()));
    }

    // ============ getById / getByPhone ============

    @Test
    @DisplayName("getById 返回时清空密码")
    void getById_masksPassword() {
        TUser u = newUser(7L, "13800000002");
        when(userMapper.selectById(7L)).thenReturn(u);

        TUser result = userService.getById(7L);
        assertThat(result.getPassword()).isNull();
    }

    @Test
    @DisplayName("getById 不存在 → USER_NOT_FOUND")
    void getById_missing() {
        when(userMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("getByPhone 返回时清空密码")
    void getByPhone_masksPassword() {
        TUser u = newUser(7L, "13800000002");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);

        TUser result = userService.getByPhone("13800000002");
        assertThat(result.getPassword()).isNull();
    }

    @Test
    @DisplayName("getByPhone 不存在 → USER_NOT_FOUND")
    void getByPhone_missing() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> userService.getByPhone("13800000099"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode()));
    }
}
