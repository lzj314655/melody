package com.melody.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源映射：把 /img/** 映射到 D:\bishe\image\ 下的本地商品图片。
 *
 * 由 D:\bishe\image\crawler\scrape_manmanbuy.py 爬取并按商品 id 分目录存放，
 * 目录结构：<image-root>/<product-id>/00.jpg, 01.png, ...
 *
 * 之所以不用 classpath:/static/：
 *   1) 图片总量 113MB+，打进 jar 体积翻倍
 *   2) 爬虫后续补图直接写磁盘即可，无需重启服务
 *   3) Windows 路径在 jar 内处理路径分隔符麻烦
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.image-root:D:\\bishe\\image}")
    private String imageRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 兼容两种写法：D:\bishe\image  与  D:/bishe/image
        Path root = Paths.get(imageRoot).toAbsolutePath().normalize();
        String location = "file:" + (root.toString().endsWith("\\") || root.toString().endsWith("/")
                ? root
                : root.resolve("")) + "/";

        registry.addResourceHandler("/img/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
