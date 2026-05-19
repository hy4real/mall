package com.macro.mall.searchmodern.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ChatServiceImpl 单元测试")
class ChatServiceImplTest {

    private MockRestServiceServer server;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        chatService = new ChatServiceImpl(
                "http://chat.test",
                "qwen3:1.7b",
                "",
                5000,
                restTemplate);
    }

    @Test
    @DisplayName("普通 API 返回 choices message 内容")
    void chat_success_returnsMessageContent() {
        server.expect(requestTo("http://chat.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"为您推荐索尼耳机"}}]}
                        """, MediaType.APPLICATION_JSON));

        String result = chatService.chat("system", "user");

        assertThat(result).isEqualTo("为您推荐索尼耳机");
        server.verify();
    }

    @Test
    @DisplayName("普通 API 异常时返回兜底消息")
    void chat_apiError_returnsFallback() {
        server.expect(requestTo("http://chat.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String result = chatService.chat("system", "user");

        assertThat(result).contains("抱歉");
        server.verify();
    }

    @Test
    @DisplayName("流式 API 解析 data chunk 并逐段输出 token")
    void streamChat_success_emitsTokens() {
        server.expect(requestTo("http://chat.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        data: {"choices":[{"delta":{"content":"为您"}}]}

                        data: {"choices":[{"delta":{"content":"推荐"}}]}

                        data: {"choices":[{"delta":{"content":"索尼耳机"}}]}

                        data: [DONE]

                        """, MediaType.TEXT_EVENT_STREAM));
        List<String> tokens = new ArrayList<>();

        String result = chatService.streamChat("system", "user", tokens::add);

        assertThat(tokens).containsExactly("为您", "推荐", "索尼耳机");
        assertThat(result).isEqualTo("为您推荐索尼耳机");
        server.verify();
    }

    @Test
    @DisplayName("流式 API 异常时抛出异常且不产生 token")
    void streamChat_apiError_throws() {
        server.expect(requestTo("http://chat.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        List<String> tokens = new ArrayList<>();

        assertThatThrownBy(() -> chatService.streamChat("system", "user", tokens::add))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI streaming service call failed");
        assertThat(tokens).isEmpty();
        server.verify();
    }
}
