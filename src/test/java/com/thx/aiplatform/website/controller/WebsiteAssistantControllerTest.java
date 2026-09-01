package com.thx.aiplatform.website.controller;
import com.thx.aiplatform.website.service.WebsiteAssistantService;
import com.thx.aiplatform.website.config.WebsiteAssistantProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器层测试：只验证协议行为（SSE 格式、参数校验、兜底文案），
 * 用 MockMvc standalone 直接装配控制器，不启动 Spring 上下文，跑得快且不依赖外部模型。
 */
class WebsiteAssistantControllerTest {

    @Test
    void 合法请求以Sse返回模型文本和结束标记() throws Exception {
        WebsiteAssistantService service = mock(WebsiteAssistantService.class);
        when(service.stream(any())).thenReturn(Flux.just("你好", "，这里是首页。"));
        MockMvc mockMvc = createMockMvc(service);

        MvcResult pending = mockMvc.perform(post("/api/public/v1/website/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"conversationId":"conversation-1","message":"这里是什么网站？"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:你好")))
                .andExpect(content().string(containsString("data:[DONE]")));
    }

    @Test
    void 空消息在调用模型前返回四百() throws Exception {
        WebsiteAssistantService service = mock(WebsiteAssistantService.class);
        MockMvc mockMvc = createMockMvc(service);

        mockMvc.perform(post("/api/public/v1/website/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"conversation-1","message":" "}
                                """))
                .andExpect(status().isBadRequest());
    }

    private MockMvc createMockMvc(WebsiteAssistantService service) {
        WebsiteAssistantProperties properties = new WebsiteAssistantProperties();
        WebsiteAssistantController controller = new WebsiteAssistantController(service, properties);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter()
                )
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }
}
