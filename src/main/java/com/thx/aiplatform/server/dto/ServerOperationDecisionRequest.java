package com.thx.aiplatform.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户对临时命令审批卡片的选择；补充说明会作为可信的用户决策恢复同一任务。 */
public record ServerOperationDecisionRequest(
        @NotBlank(message = "请选择处理方式")
        @Pattern(regexp = "EXECUTE_ONCE|EXECUTE_AND_REMEMBER|REJECT_WITH_FEEDBACK", message = "处理方式不合法")
        String decision,
        @Size(max = 1000, message = "补充说明不能超过 1000 个字符")
        String feedback
) { }
