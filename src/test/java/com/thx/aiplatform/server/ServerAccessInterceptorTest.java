package com.thx.aiplatform.server;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ServerAccessInterceptorTest {

    @Test
    void 只有配置的Bearer口令可以访问服务器接口() throws Exception {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setAccessToken("correct-secret");
        ServerAccessInterceptor interceptor = new ServerAccessInterceptor(properties);

        MockHttpServletRequest validRequest = new MockHttpServletRequest();
        validRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer correct-secret");
        assertThat(interceptor.preHandle(validRequest, new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletRequest invalidRequest = new MockHttpServletRequest();
        invalidRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer wrong-secret");
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(invalidRequest, invalidResponse, new Object())).isFalse();
        assertThat(invalidResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void 服务端未配置口令时拒绝开放服务器接口() throws Exception {
        ServerAccessInterceptor interceptor = new ServerAccessInterceptor(new ServerAssistantProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
    }
}
