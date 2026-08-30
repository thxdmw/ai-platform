package com.thx.aiplatform.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogPublicationRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 个字符")
        String title,

        @NotBlank(message = "Markdown 正文不能为空")
        @Size(max = 100_000, message = "Markdown 正文不能超过 100000 个字符")
        String contentMd,

        @Size(max = 64, message = "分类 ID 不能超过 64 个字符")
        String categoryId,

        @Size(max = 500, message = "标签 ID 不能超过 500 个字符")
        String tagIds,

        @Size(max = 500, message = "摘要不能超过 500 个字符")
        String description,

        @Size(max = 500, message = "关键词不能超过 500 个字符")
        String keywords,

        @Size(max = 2048, message = "封面地址不能超过 2048 个字符")
        String coverImage,

        @Size(max = 100, message = "作者名不能超过 100 个字符")
        String author
) {
    public BlogPublicationRequest normalized() {
        return new BlogPublicationRequest(
                title.trim(),
                contentMd.trim(),
                categoryId,
                trimToNull(tagIds),
                trimToNull(description),
                trimToNull(keywords),
                trimToNull(coverImage),
                trimToNull(author)
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
