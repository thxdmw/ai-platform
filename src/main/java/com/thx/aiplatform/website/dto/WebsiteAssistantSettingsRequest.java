package com.thx.aiplatform.website.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 网站助手设置保存请求。 */
public record WebsiteAssistantSettingsRequest(
        @NotBlank(message = "助手名称不能为空")
        @Size(max = 80, message = "助手名称不能超过 80 个字符")
        String assistantName,

        @NotBlank(message = "欢迎语不能为空")
        @Size(max = 300, message = "欢迎语不能超过 300 个字符")
        String welcomeMessage,

        @Size(max = 4000, message = "补充回答规则不能超过 4000 个字符")
        String promptAddition,

        boolean enabled
) {
}
