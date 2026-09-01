package com.thx.aiplatform.server.config;
import com.thx.aiplatform.server.controller.ServerAssistantController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 服务器助手模块的异常 → HTTP 状态映射：参数/业务规则错误（IllegalArgumentException）
 * 返回 400，状态冲突（如确认选项已过期、操作状态不对）返回 409。用 basePackageClasses
 * 把处理器限定在本模块控制器上，避免改写平台其他模块的错误响应。
 */
@RestControllerAdvice(basePackageClasses = ServerAssistantController.class)
class ServerApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
