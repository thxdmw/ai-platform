package com.thx.aiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 平台启动入口。
 * <p>{@code @ConfigurationPropertiesScan} 让各模块的 @ConfigurationProperties 类
 * （如 WebsiteAssistantProperties）无需逐个显式注册即可生效，新增配置类零样板。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
    }
}
