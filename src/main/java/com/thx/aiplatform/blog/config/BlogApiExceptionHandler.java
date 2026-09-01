package com.thx.aiplatform.blog.config;
import com.thx.aiplatform.blog.controller.BlogAssistantController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 博客助手 REST API 的统一异常出口：把业务异常翻译成 {"message":"..."} 响应结构。
 * 固定这一形状是为了让前端对所有错误只有一种解析假设，避免各端点错误结构不一致。
 */
@RestControllerAdvice(basePackageClasses = BlogAssistantController.class)
class BlogApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    /**
     * 只取第一个字段错误向外报告：校验失败时逐条罗列会让前端信息过载，一次改一个更友好。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
