package com.thx.aiplatform.website.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 公开接口的请求体，由字段级 Bean Validation 在进入控制器前完成校验。
 * <p>conversationId 限定为字母/数字/下划线/连字符：它最终会被拼进对话记忆的 key 并出现在
 * 日志里，放开字符集等于把任意文本引入这两处，存在日志注入与记忆 key 冲突的风险；
 * message 限长 500 是对输入量的硬上限——公开接口必须为滥用成本设闸。</p>
 */
public record WebsiteChatRequest(
        @NotBlank(message = "会话编号不能为空")
        @Size(max = 80, message = "会话编号过长")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "会话编号格式不正确")
        String conversationId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 500, message = "消息内容不能超过 500 个字符")
        String message
) {
}
