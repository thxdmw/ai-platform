package com.thx.aiplatform.blog.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 访问口令拦截器：覆盖口令匹配与未配置口令两种情况。 */
class BlogAccessInterceptorTest {

    @Test
    void 只有配置的Bearer口令可以访问博客接口() throws Exception {
        BlogAssistantProperties properties = new BlogAssistantProperties();
        properties.setAccessToken("correct-secret");
        BlogAccessInterceptor interceptor = new BlogAccessInterceptor(properties);

        MockHttpServletRequest validRequest = new MockHttpServletRequest();
        validRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer correct-secret");
        MockHttpServletResponse validResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(validRequest, validResponse, new Object())).isTrue();

        MockHttpServletRequest invalidRequest = new MockHttpServletRequest();
        invalidRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer wrong-secret");
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(invalidRequest, invalidResponse, new Object())).isFalse();
        assertThat(invalidResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void 服务端未配置口令时拒绝开放博客接口() throws Exception {
        BlogAccessInterceptor interceptor = new BlogAccessInterceptor(new BlogAssistantProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
    }
}
