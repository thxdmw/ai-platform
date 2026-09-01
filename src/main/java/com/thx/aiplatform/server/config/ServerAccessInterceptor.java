package com.thx.aiplatform.server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/server/v1/** 的 Bearer 口令拦截器。整个 API 面都暴露服务器列表、命令明细与危险
 * 操作确认端点，一旦放开任何人都能读取主机信息甚至触发执行，因此必须整体加锁。口令未
 * 配置时返回 503 而不是 401：语义是「服务未就绪」而非「口令错误」，避免前端把两者混为
 * 一谈。
 */
@Component
class ServerAccessInterceptor implements org.springframework.web.servlet.HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ServerAssistantProperties properties;

    ServerAccessInterceptor(ServerAssistantProperties properties) { this.properties = properties; }

    /**
     * 校验 Bearer 口令。用 MessageDigest.isEqual 做常量时间比较而不是 String.equals：
     * 口令来自请求头，equals 的短路行为会让攻击者通过响应耗时逐字符试探出正确口令。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String configured = properties.getAccessToken();
        if (configured.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "服务器助手访问口令尚未配置");
            return false;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String provided = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim() : "";
        if (!MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "服务器助手访问口令无效");
            return false;
        }
        return true;
    }
}
