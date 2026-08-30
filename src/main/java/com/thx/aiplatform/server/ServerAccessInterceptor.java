package com.thx.aiplatform.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
class ServerAccessInterceptor implements org.springframework.web.servlet.HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ServerAssistantProperties properties;

    ServerAccessInterceptor(ServerAssistantProperties properties) { this.properties = properties; }

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
