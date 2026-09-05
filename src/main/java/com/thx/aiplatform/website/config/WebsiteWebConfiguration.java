package com.thx.aiplatform.website.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 网站助手公开接口的 Web 层配置：精确 origin 白名单的 CORS + 限流拦截器注册。
 * <p>公开无鉴权接口的安全边界全部在 Web 层收口，业务代码无需感知；
 * {@code allowCredentials(false)} 表示接口不依赖 cookie/凭证，从根上避开
 * 浏览器对「凭证 + 非通配 origin」组合的各种限制。</p>
 */
@Configuration(proxyBeanMethods = false)
class WebsiteWebConfiguration implements WebMvcConfigurer {

    private static final String API_PATH = "/api/public/v1/website/**";

    private final WebsiteAssistantProperties properties;
    private final WebsiteRateLimitInterceptor rateLimitInterceptor;
    private final WebsiteAccessInterceptor accessInterceptor;

    WebsiteWebConfiguration(
            WebsiteAssistantProperties properties,
            WebsiteRateLimitInterceptor rateLimitInterceptor,
            WebsiteAccessInterceptor accessInterceptor
    ) {
        this.properties = properties;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.accessInterceptor = accessInterceptor;
    }

    // 配置读取需要 GET，对话需要 POST，新建对话前用 DELETE 清理旧记忆，OPTIONS 用于跨域预检；精确枚举 allowedOrigins 而非 *，
    // 是「公开但只面向指定站点」的安全取舍——放弃通配的便利，换来可控的暴露面。
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(API_PATH)
                .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(API_PATH);
        registry.addInterceptor(accessInterceptor).addPathPatterns("/api/website/v1/**");
    }
}
