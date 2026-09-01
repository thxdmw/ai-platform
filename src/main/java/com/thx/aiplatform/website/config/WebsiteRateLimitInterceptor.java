package com.thx.aiplatform.website.config;
import com.thx.aiplatform.website.service.WebsiteRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 公开接口的 IP 限流拦截器，是「CORS 白名单 + 固定助手编号 + IP 限流」三层安全中的最后一层。
 * <p>按请求方 IP 简单计数，属于尽力而为的粗粒度保护，目的只是挡住脚本刷接口；
 * 刻意不做客户端指纹等精确识别——那会引入状态复杂度和误伤真实用户的风险，收益不成比例。</p>
 */
@Component
class WebsiteRateLimitInterceptor implements HandlerInterceptor {

    private final WebsiteRateLimiter rateLimiter;

    WebsiteRateLimitInterceptor(WebsiteRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    // 只对 POST 计数：GET/OPTIONS 预检请求不占用配额，否则浏览器的 CORS 预检瞬间就能把配额耗光。
    // 被限流时直接写 429 响应而非抛异常走异常处理器，保证时序与响应格式完全可控。
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
