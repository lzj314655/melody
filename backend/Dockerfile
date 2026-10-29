# ============ 麦乐迪后端：多阶段构建 ============
# 构建阶段：阿里云 Maven 镜像源 + 跳过测试
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# settings.xml 把所有仓库镜像到阿里云（仅镜像构建期生效）
COPY docker-maven-settings.xml /root/.m2/settings.xml

# 先拷 pom 单独解析依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn -q dependency:go-offline

# 再拷源码编译打包
COPY src ./src
RUN mvn -q package -DskipTests

# 运行阶段：仅 JRE
FROM eclipse-temurin:17-jre
WORKDIR /app

# 容器内默认配置（可被 compose 环境变量覆盖，application.yml 中均为 ${ENV:default} 占位）
ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/melody_shop?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true \
    SPRING_DATASOURCE_USERNAME=root \
    SPRING_DATASOURCE_PASSWORD=123456 \
    SPRING_DATA_REDIS_HOST=redis \
    SPRING_DATA_REDIS_PORT=6379 \
    APP_IMAGE_ROOT=/app/image \
    TZ=Asia/Shanghai

COPY --from=build /build/target/melody-shop-backend-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
