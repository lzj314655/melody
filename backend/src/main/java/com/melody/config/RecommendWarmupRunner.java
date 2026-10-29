package com.melody.config;

import com.melody.task.RecommendTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 推荐缓存预热（PRD §4.3.4）
 *
 * 后端启动完成后立即跑一次 ItemCF 离线计算并写入 Redis，保证 /api/recommend/* 首次请求即命中缓存。
 * 否则 Redis 一直为空，所有推荐请求都退化成实时计算（computePersonalOnTheFly），Redis 形同虚设。
 *
 * demo 数据量（几百条行为）几十毫秒完成，不阻塞正常服务；失败也不影响启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendWarmupRunner implements ApplicationRunner {

    private final RecommendTask recommendTask;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int affected = recommendTask.rebuildAll();
            log.info("[Warmup] 推荐缓存预热完成，写入用户推荐 {} 个，Redis 已就绪", affected);
        } catch (Exception ex) {
            log.warn("[Warmup] 推荐缓存预热失败（不影响启动）: {}", ex.getMessage());
        }
    }
}
