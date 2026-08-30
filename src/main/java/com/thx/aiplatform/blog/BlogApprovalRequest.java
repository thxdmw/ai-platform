package com.thx.aiplatform.blog;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record BlogApprovalRequest(
        @NotBlank(message = "请输入“发布”确认操作")
        @Pattern(regexp = "发布", message = "请输入“发布”确认操作")
        String confirmation
) {
}
