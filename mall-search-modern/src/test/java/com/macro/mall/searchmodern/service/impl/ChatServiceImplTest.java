package com.macro.mall.searchmodern.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl 单元测试")
class ChatServiceImplTest {

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        // Use unreachable URL to test error fallback path
        chatService = new ChatServiceImpl(
                "http://localhost:19999",
                "qwen3:1.7b",
                "",
                5000);
    }

    @Test
    @DisplayName("API 不可达时返回错误兜底消息")
    void chat_apiUnreachable_returnsFallback() {
        String result = chatService.chat("system", "user");
        assertThat(result).contains("抱歉");
    }

    @Test
    @DisplayName("key 为空时仍然尝试调用（本地 Ollama 无需认证）")
    void chat_emptyKey_stillAttemptsCall() {
        String result = chatService.chat("system", "user");
        // Should get an error message (connection refused), not "未配置"
        assertThat(result).doesNotContain("未配置");
    }
}
