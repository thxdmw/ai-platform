package com.thx.aiplatform.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 博客模块的 Web 装配：把访问口令拦截器挂到 /api/blog/v1/**。
 * 只保护 API 路径——页面壳子是公开的，敏感数据全在 API 后面；
 * 拦截器若覆盖静态资源，登录页自身的资源都会加载不出来。
 */
@Configuration
class BlogWebConfiguration implements WebMvcConfigurer {

    private final BlogAccessInterceptor accessInterceptor;

    BlogWebConfiguration(BlogAccessInterceptor accessInterceptor) {
        this.accessInterceptor = accessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessInterceptor)
                .addPathPatterns("/api/blog/v1/**");
    }
}
