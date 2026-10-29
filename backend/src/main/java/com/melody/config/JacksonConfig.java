package com.melody.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置：统一 LocalDate / LocalDateTime 序列化格式
 * （Spring Boot 3 默认包含 jackson-datatype-jsr310，需手动注册序列化器）
 */
@Configuration
public class JacksonConfig {

    private static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    private static final String PATTERN_DATE = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern(PATTERN_DATETIME);
            DateTimeFormatter dFmt = DateTimeFormatter.ofPattern(PATTERN_DATE);

            // 序列化：LocalDateTime / LocalDate → 字符串
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dtFmt));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(dFmt));
            // 反序列化：字符串 → LocalDateTime / LocalDate
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dtFmt));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(dFmt));

            // Long 类型 ID 防止前端 JS 精度丢失（序列化为字符串）
            // builder.serializerByType(Long.class, ToStringSerializer.instance);

            builder.modulesToInstall(new JavaTimeModule());
        };
    }
}