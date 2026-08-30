package com.thx.aiplatform.blog;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
