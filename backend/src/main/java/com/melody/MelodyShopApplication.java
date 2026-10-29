package com.melody;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 麦乐迪零食购物商场 - Spring Boot 启动类
 *
 * @author lzj
 */
@SpringBootApplication
@EnableAsync              // 启用行为埋点异步落库
@EnableScheduling         // 启用推荐系统定时任务（每日凌晨 3 点 Item-CF 离线计算）
@MapperScan("com.melody.mapper")
public class MelodyShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(MelodyShopApplication.class, args);
    }
}

