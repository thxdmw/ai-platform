package com.thx.aiplatform.server.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 命令创建/更新请求体：必填与长度上限在 @Valid 层先拦一道。命令文本最长 8000 字符——
 * 这是单条命令的上限，防止把超长脚本塞进一条记录；riskLevel 由页面/提议服务提供，
 * 解析回枚举在服务层完成。
 */
public record ServerCommandRequest(
        @NotBlank(message = "命令名称不能为空") @Size(max = 80, message = "命令名称最多 80 个字符") String name,
        @NotBlank(message = "命令用途不能为空") @Size(max = 500, message = "命令用途最多 500 个字符") String description,
        @NotBlank(message = "命令内容不能为空") @Size(max = 8000, message = "命令内容最多 8000 个字符") String commandText,
        @Size(max = 8000, message = "参数规则最多 8000 个字符") String parameterSchema,
        @NotBlank(message = "风险等级不能为空") String riskLevel,
        Boolean enabled,
        @Min(value = -10000, message = "排序值过小") @Max(value = 10000, message = "排序值过大") Integer sortOrder
) { }
