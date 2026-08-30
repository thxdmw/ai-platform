package com.thx.aiplatform.blog;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 每次请求创建一个绑定 conversationId 的实例，避免模型伪造发布候选的会话归属。
 */
public final class BlogPublicationTool {

    private final String conversationId;
    private final BlogPublicationService publicationService;

    BlogPublicationTool(String conversationId, BlogPublicationService publicationService) {
        this.conversationId = conversationId;
        this.publicationService = publicationService;
    }

    @Tool(description = "准备博客发布候选。仅当文章完整且用户明确要求发布时调用；调用后由界面让用户选择发布或取消，工具本身不会发布文章")
    public String proposePublication(
            @ToolParam(description = "文章标题") String title,
            @ToolParam(description = "完整 Markdown 正文") String contentMd,
            @ToolParam(description = "分类 ID，可不填", required = false) String categoryId,
            @ToolParam(description = "标签 ID，多个用英文逗号分隔，可不填", required = false) String tagIds,
            @ToolParam(description = "文章摘要，可不填", required = false) String description,
            @ToolParam(description = "SEO 关键词，多个用英文逗号分隔，可不填", required = false) String keywords,
            @ToolParam(description = "封面图片 URL，可不填", required = false) String coverImage,
            @ToolParam(description = "作者，可不填", required = false) String author
    ) {
        PendingPublicationView pending = publicationService.prepare(
                conversationId,
                new BlogPublicationRequest(
                        title, contentMd, categoryId, tagIds, description, keywords, coverImage, author
                )
        );
        return "已准备发布候选《" + pending.title() + "》，等待用户在界面中选择发布或取消。"
                + "不要声称文章已经发布，也不要输出内部任务编号。";
    }
}
