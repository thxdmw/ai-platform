package com.thx.aiplatform.server.vo;

/**
 * SSH 连接测试结果。测试失败以异常形式抛给异常处理器，正常路径只构造成功的结果。
 */
public record ServerConnectionTestResult(boolean success, String message) { }
