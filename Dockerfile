# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /build

# 先复制构建描述并预取依赖，使普通源码修改可以复用依赖层缓存。
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B -ntp

COPY src ./src

# Drone 已执行完整测试；镜像阶段只重新编译生产包，避免重复运行测试。
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn clean package -DskipTests -B -ntp && \
    find target -maxdepth 1 -type f -name 'ai-platform-*.jar' ! -name '*.original' -exec cp '{}' /build/app.jar \;

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S ai-platform && adduser -S ai-platform -G ai-platform
WORKDIR /app

COPY --from=builder /build/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 20005

USER ai-platform
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
