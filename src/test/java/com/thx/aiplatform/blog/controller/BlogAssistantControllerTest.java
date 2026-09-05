package com.thx.aiplatform.blog.controller;
import com.thx.aiplatform.blog.service.BlogPublicationService;
import com.thx.aiplatform.blog.service.BlogAssistantService;
import com.thx.aiplatform.blog.vo.PendingPublicationView;
import com.thx.aiplatform.blog.config.BlogAssistantProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 聊天接口：验证 SSE 文本输出、流结束时的 action 事件携带待发布选项。 */
class BlogAssistantControllerTest {

    @Test
    void 合法请求以Sse返回博客助手文本() throws Exception {
        BlogAssistantService service = mock(BlogAssistantService.class);
        when(service.stream(any())).thenReturn(Flux.just("这是", "博客回答"));
        BlogAssistantProperties properties = new BlogAssistantProperties();
        BlogPublicationService publicationService = mock(BlogPublicationService.class);
        when(publicationService.findForConversation("session-1")).thenReturn(new PendingPublicationView(
                "action-1", "待发布文章", "摘要", "category-1", "tag-1", 1200,
                Instant.parse("2026-08-31T00:15:00Z"), "PENDING_APPROVAL"));
        BlogAssistantController controller = new BlogAssistantController(service, properties, publicationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter()
                )
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();

        MvcResult pending = mockMvc.perform(post("/api/blog/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"conversationId":"session-1","message":"查询最新文章"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:这是")))
                .andExpect(content().string(containsString("event:action")))
                .andExpect(content().string(containsString("待发布文章")))
                .andExpect(content().string(containsString("data:[DONE]")));
    }
}
