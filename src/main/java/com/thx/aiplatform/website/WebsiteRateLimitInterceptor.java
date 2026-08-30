package com.thx.aiplatform.website;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
class WebsiteRateLimitInterceptor implements HandlerInterceptor {

    private final WebsiteRateLimiter rateLimiter;

    WebsiteRateLimitInterceptor(WebsiteRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (rateLimiter.tryAcquire(request.getRemoteAddr())) {
            return true;
        }

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
        return false;
    }
}
