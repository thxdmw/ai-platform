package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.model.ServerCommandParameterDefinition;

import java.util.List;

/**
 * 参数化命令的安全边界：模板只能在完整 Shell 参数位置使用 {{name}}，参数先按类型与
 * 白名单校验，再统一做 POSIX Shell 单引号转义。
 */
public interface ServerCommandTemplateService {

    String normalizeSchema(String template, String schemaJson);

    List<ServerCommandParameterDefinition> parameters(ServerCommandEntity command);

    String render(ServerCommandEntity command, String argumentsJson);

    String classificationText(String template, String schemaJson);
}