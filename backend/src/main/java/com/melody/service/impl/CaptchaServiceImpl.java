package com.melody.service.impl;

import com.melody.service.ICaptchaService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片验证码服务 - 内存版
 *
 * - 4 位字符(数字 + 大写字母,去除易混的 0/O/1/I/L)
 * - 5 分钟过期
 * - 一次性消费(校验后删除)
 *
 * 毕设简化版,生产应改 Redis
 */
@Service
public class CaptchaServiceImpl implements ICaptchaService {

    private static final long EXPIRE_MS = 5 * 60 * 1000;
    private static final char[] CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    /** id → (code, createTime) */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String generateCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(CHARS[RNG.nextInt(CHARS.length)]);
        return sb.toString();
    }

    @Override
    public String create() {
        String id = UUID.randomUUID().toString();
        store.put(id, new Entry(generateCode(), System.currentTimeMillis()));
        // 顺手清理过期
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now - e.getValue().createAt > EXPIRE_MS);
        return id;
    }

    @Override
    public String getCode(String id) {
        Entry e = store.get(id);
        if (e == null) return null;
        if (System.currentTimeMillis() - e.createAt > EXPIRE_MS) {
            store.remove(id);
            return null;
        }
        return e.code;
    }

    @Override
    public boolean verify(String id, String code) {
        String real = getCode(id);
        if (real == null || code == null) return false;
        boolean ok = real.equalsIgnoreCase(code);
        // 一次性消费
        store.remove(id);
        return ok;
    }

    private record Entry(String code, long createAt) {}
}