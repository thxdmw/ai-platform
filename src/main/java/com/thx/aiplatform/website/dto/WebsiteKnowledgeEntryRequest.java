package com.thx.aiplatform.website.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.thx.aiplatform.website.model.WebsiteKnowledgeEntryType;

/** 知识条目新增/编辑请求。 */
public record WebsiteKnowledgeEntryRequest(
        @NotNull(message = "知识类型不能为空")
        WebsiteKnowledgeEntryType entryType,

        @NotBlank(message = "标题不能为空")
        @Size(max = 160, message = "标题不能超过 160 个字符")
        String title,

        @Size(max = 500, message = "问题不能超过 500 个字符")
        String question,

        @NotBlank(message = "内容/答案不能为空")
        @Size(max = 12_000, message = "单条知识不能超过 12000 个字符")
        String content,

        @Size(max = 500, message = "关键词不能超过 500 个字符")
        String keywords,

        boolean enabled,

        @Min(value = 0, message = "优先级不能小于 0")
        @Max(value = 1000, message = "优先级不能大于 1000")
        int priority
) {
}
