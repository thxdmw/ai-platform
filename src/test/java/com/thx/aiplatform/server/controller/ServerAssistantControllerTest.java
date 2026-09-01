package com.thx.aiplatform.server.controller;

import com.thx.aiplatform.platform.AssistantStreamEvent;
import com.thx.aiplatform.server.config.ServerAssistantProperties;
import com.thx.aiplatform.server.service.ServerAssistantService;
import com.thx.aiplatform.server.service.ServerCommandProposalService;
import com.thx.aiplatform.server.service.ServerOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class ServerAssistantControllerTest {

    @Test
    void 事件流能区分思考和正文并正常结束() throws Exception {
        ServerAssistantService assistantService = mock(ServerAssistantService.class);
        when(assistantService.stream(any())).thenReturn(Flux.just(
                AssistantStreamEvent.reasoning("reasoning"), AssistantStreamEvent.content("complete")));
        ServerOperationService operationService = mock(ServerOperationService.class);
        ServerCommandProposalService proposalService = mock(ServerCommandProposalService.class);
        when(operationService.findForConversation(anyString())).thenReturn(null);
        when(proposalService.findForConversation(anyString())).thenReturn(null);
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setSseTimeout(Duration.ofSeconds(5));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ServerAssistantController(
                assistantService, operationService, properties, proposalService)).build();

        MvcResult pending = mvc.perform(post("/api/server/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"conversation-1","serverId":"server-a","message":"检查",\
                                "modelId":null,"reasoningEffort":"high"}
                                """))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult completed = mvc.perform(asyncDispatch(pending)).andReturn();

        String body = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:reasoning", "reasoning", "complete", "[DONE]");
    }

    @Test
    void 建流前的业务异常也会按事件流收尾而不是交给Json转换器() throws Exception {
        ServerAssistantService assistantService = mock(ServerAssistantService.class);
        when(assistantService.stream(any())).thenThrow(new IllegalStateException("会话状态冲突"));
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setSseTimeout(Duration.ofSeconds(5));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ServerAssistantController(
                assistantService, mock(ServerOperationService.class), properties,
                mock(ServerCommandProposalService.class))).build();

        MvcResult pending = mvc.perform(post("/api/server/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"conversation-1","serverId":"server-a","message":"检查",\
                                "modelId":null,"reasoningEffort":"auto"}
                                """))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult completed = mvc.perform(asyncDispatch(pending)).andReturn();

        assertThat(completed.getResponse().getContentAsString()).contains("[DONE]").doesNotContain("{\"message\"");
    }
}
