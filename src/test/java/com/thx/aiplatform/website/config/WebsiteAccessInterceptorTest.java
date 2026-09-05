package com.thx.aiplatform.website.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 网站助手后台只接受服务端配置的 Bearer 口令。 */
class WebsiteAccessInterceptorTest {

    @Test
    void 后台口令匹配时放行() throws Exception {
        WebsiteAssistantProperties properties = new WebsiteAssistantProperties();
        properties.setAccessToken("correct-secret");
        WebsiteAccessInterceptor interceptor = new WebsiteAccessInterceptor(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer correct-secret");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void 口令未配置时后台保持关闭() throws Exception {
        WebsiteAccessInterceptor interceptor = new WebsiteAccessInterceptor(new WebsiteAssistantProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
    }
}
