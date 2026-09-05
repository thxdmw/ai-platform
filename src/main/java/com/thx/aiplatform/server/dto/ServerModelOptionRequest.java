package com.thx.aiplatform.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServerModelOptionRequest(
        @NotBlank(message = "模型显示名称不能为空") @Size(max = 80) String name,
        @NotBlank(message = "模型编号不能为空") @Size(max = 160) String modelCode,
        @Size(max = 30) String reasoningEffort,
        boolean enabled,
        int sortOrder
) { }
