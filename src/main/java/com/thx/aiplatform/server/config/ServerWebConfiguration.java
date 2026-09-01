package com.thx.aiplatform.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：把访问口令拦截器挂到 /api/server/v1/** 上。只拦 API 前缀——页面静态资源
 * 本身不含敏感数据，真正的数据都经由被拦截的 API 获取，未授权请求拿不到任何凭据或
 * 命令明细。
 */
@Configuration
class ServerWebConfiguration implements WebMvcConfigurer {

    private final ServerAccessInterceptor interceptor;

    ServerWebConfiguration(ServerAccessInterceptor interceptor) { this.interceptor = interceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/server/v1/**");
    }
}
