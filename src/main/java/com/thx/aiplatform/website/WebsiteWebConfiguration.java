package com.thx.aiplatform.website;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebsiteWebConfiguration implements WebMvcConfigurer {

    private static final String API_PATH = "/api/public/v1/website/**";

    private final WebsiteAssistantProperties properties;
    private final WebsiteRateLimitInterceptor rateLimitInterceptor;

    WebsiteWebConfiguration(
            WebsiteAssistantProperties properties,
            WebsiteRateLimitInterceptor rateLimitInterceptor
    ) {
        this.properties = properties;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(API_PATH)
                .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(API_PATH);
    }
}
