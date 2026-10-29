package com.melody.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.common.R;
import com.melody.common.ResultCode;
import com.melody.entity.TPointLog;
import com.melody.entity.TGrowthLog;
import com.melody.entity.TUser;
import com.melody.entity.TWalletAccount;
import com.melody.exception.BizException;
import com.melody.mapper.TPointLogMapper;
import com.melody.mapper.TGrowthLogMapper;
import com.melody.mapper.TUserMapper;
import com.melody.mapper.TWalletAccountMapper;
import com.melody.service.IUserService;
import com.melody.service.impl.OperationLogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台用户管理（PRD §8.2 /api/admin/user，F-A-040 ~ F-A-044）
 */
@RestController
@RequestMapping("/api/admin/user")
@PreAuthorize("hasAuthority('user:read')")
@RequiredArgsConstructor
public class AdminUserController {

    private final IUserService userService;
    private final TUserMapper userMapper;
    private final TWalletAccountMapper walletMapper;
    private final TPointLogMapper pointLogMapper;
    private final TGrowthLogMapper growthLogMapper;
    private final OperationLogServiceImpl opLog;

    /** 用户列表（带钱包余额） */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String rank) {
        List<TUser> users = userMapper.selectList(
                new LambdaQueryWrapper<TUser>().orderByDesc(TUser::getId));
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            users = users.stream().filter(u ->
                (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw)) ||
                (u.getNickname() != null && u.getNickname().toLowerCase().contains(kw)) ||
                (u.getPhone() != null && u.getPhone().contains(kw)) ||
                String.valueOf(u.getId()).contains(kw)
            ).collect(Collectors.toList());
        }
        if (rank != null && !rank.isBlank() && !"全部".equals(rank)) {
            String code = rankToCode(rank);
            users = users.stream().filter(u -> code.equals(u.getRankCode())).collect(Collectors.toList());
        }
        // 拼接钱包余额
        List<Long> ids = users.stream().map(TUser::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> walletMap = new HashMap<>();
        if (!ids.isEmpty()) {
            walletMapper.selectBatchIds(ids).forEach(w -> walletMap.put(w.getUserId(), w.getBalance()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (TUser u : users) {
            u.setPassword(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("nickname", u.getNickname());
            map.put("phone", u.getPhone());
            map.put("email", u.getEmail());
            map.put("rankCode", u.getRankCode());
            map.put("rank", codeToRank(u.getRankCode()));
            map.put("points", u.getPoints() == null ? 0 : u.getPoints());
            map.put("growthValue", u.getGrowthValue() == null ? 0 : u.getGrowthValue());
            map.put("balance", walletMap.getOrDefault(u.getId(), BigDecimal.ZERO));
            map.put("status", u.getStatus());
            map.put("joinDate", u.getJoinDate());
            map.put("lastLoginAt", u.getLastLoginAt());
            result.add(map);
        }
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        TUser u = userService.getById(id);
        TWalletAccount w = walletMapper.selectOne(
                new LambdaQueryWrapper<TWalletAccount>().eq(TWalletAccount::getUserId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("nickname", u.getNickname());
        map.put("phone", u.getPhone());
        map.put("email", u.getEmail());
        map.put("rankCode", u.getRankCode());
        map.put("points", u.getPoints());
        map.put("balance", w != null ? w.getBalance() : BigDecimal.ZERO);
        map.put("status", u.getStatus());
        map.put("joinDate", u.getJoinDate());
        return R.ok(map);
    }

    /** F-A-042: 修改用户状态（启用/拉黑） */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:adjust')")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        if (status == null) throw new IllegalArgumentException("status 必填");
        TUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        u.setStatus(status);
        userMapper.updateById(u);
        opLog.log("user", (status == 0 ? "冻结" : "解冻") + "用户 id=" + id + " (" + (u.getUsername() == null ? "" : u.getUsername()) + ")");
        return R.<Void>okMsg("用户状态已更新");
    }

    /** F-A-043: 赠送积分（同步累加成长值并按阈值升档） */
    @PostMapping("/{id}/gift-points")
    @PreAuthorize("hasAuthority('user:adjust')")
    public R<Map<String, Object>> giftPoints(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer amount = (Integer) body.get("amount");
        if (amount == null || amount <= 0) throw new IllegalArgumentException("amount 必须为正整数");
        TUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        int newPoints = (u.getPoints() == null ? 0 : u.getPoints()) + amount;
        int newGrowth = (u.getGrowthValue() == null ? 0 : u.getGrowthValue()) + amount;
        String newRank = computeRank(newGrowth);
        u.setPoints(newPoints);
        u.setGrowthValue(newGrowth);
        if (newRank != null && !newRank.equals(u.getRankCode())) u.setRankCode(newRank);
        userMapper.updateById(u);
        // 写积分流水
        TPointLog pl = new TPointLog();
        pl.setUserId(id);
        pl.setChangeAmount(amount);
        pl.setBalanceAfter(newPoints);
        pl.setType("GIFT");
        pl.setReason("管理员赠送");
        pointLogMapper.insert(pl);
        // 写成长值流水
        TGrowthLog gl = new TGrowthLog();
        gl.setUserId(id);
        gl.setChangeAmount(amount);
        gl.setBalanceAfter(newGrowth);
        gl.setType("GIFT");
        gl.setReason("管理员赠送");
        gl.setRelatedOrderId(null);
        growthLogMapper.insert(gl);
        Map<String, Object> data = new HashMap<>();
        data.put("points", newPoints);
        data.put("growthValue", newGrowth);
        data.put("rankCode", u.getRankCode());
        data.put("rank", codeToRank(u.getRankCode()));
        opLog.log("user", "赠送用户 id=" + id + " 积分 +" + amount + "（成长值 +" + amount + "，等级 " + u.getRankCode() + "）");
        return R.ok("赠送成功", data);
    }

    private static final int RANK_V1 = 500;
    private static final int RANK_V2 = 2000;
    private static final int RANK_V3 = 5000;
    private static String computeRank(int growth) {
        if (growth >= RANK_V3) return "V3";
        if (growth >= RANK_V2) return "V2";
        if (growth >= RANK_V1) return "V1";
        return "V0";
    }

    private static String rankToCode(String label) {
        return switch (label) {
            case "钻石会员" -> "V3";
            case "金牌会员" -> "V2";
            case "银牌会员" -> "V1";
            default -> "V0";
        };
    }

    private static String codeToRank(String code) {
        return switch (code == null ? "V0" : code) {
            case "V3" -> "钻石会员";
            case "V2" -> "金牌会员";
            case "V1" -> "银牌会员";
            default -> "普通会员";
        };
    }
}