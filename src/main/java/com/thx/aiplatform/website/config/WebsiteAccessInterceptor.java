package com.thx.aiplatform.website.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 网站助手后台 API 的独立口令校验，口令只能由环境变量提供。 */
@Component
class WebsiteAccessInterceptor implements org.springframework.web.servlet.HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private final WebsiteAssistantProperties properties;

    WebsiteAccessInterceptor(WebsiteAssistantProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expected = properties.getAccessToken();
        if (expected.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "网站助手访问口令尚未配置");
            return false;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String actual = header != null && header.startsWith(BEARER_PREFIX)
                ? header.substring(BEARER_PREFIX.length()).trim()
                : "";
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "网站助手访问口令无效");
            return false;
        }
        return true;
    }
}
