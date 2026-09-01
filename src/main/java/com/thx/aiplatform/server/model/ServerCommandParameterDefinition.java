package com.thx.aiplatform.server.model;

import java.util.List;

/**
 * 命令模板参数规则。所有值在进入 Shell 前都必须经过服务端按这里的规则校验和引用，
 * 模型只负责给出候选值，不能自行拼接命令。
 */
public record ServerCommandParameterDefinition(
        String name,
        ServerCommandParameterType type,
        List<String> allowedValues,
        List<String> allowedRoots,
        String pattern,
        Integer maxLength,
        Long minValue,
        Long maxValue
) { }
