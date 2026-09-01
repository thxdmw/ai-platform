package com.thx.aiplatform.blog.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 博客助手接口的访问控制：校验请求头 Authorization 里的 Bearer 口令，
 * 口令未配置时整个接口以 503 拒绝服务——宁可不可用，也不开放。
 * 口令比较必须走 {@link MessageDigest#isEqual} 常量时间比较，防止按字符短路的时序侧信道探测口令。
 */
@Component
class BlogAccessInterceptor implements org.springframework.web.servlet.HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final BlogAssistantProperties properties;

    BlogAccessInterceptor(BlogAssistantProperties properties) {
        this.properties = properties;
    }

    /**
     * 未配置口令返回 503 而非 401：401 语义是「凭据无效、可重试」，而这里是部署未完成，
     * 503 直接提示运维去配置，避免前端误以为换个口令就能通过。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String configuredToken = properties.getAccessToken();
        if (configuredToken.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "博客助手访问口令尚未配置");
            return false;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String providedToken = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : "";
        if (!constantTimeEquals(configuredToken, providedToken)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "博客助手访问口令无效");
            return false;
        }
        return true;
    }

    /**
     * 字符串 equals 在首个不匹配字符处就提前返回，耗时随匹配前缀长度变化；
     * isEqual 的耗时与内容无关，避免攻击者借响应时间差逐位探测口令。
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
