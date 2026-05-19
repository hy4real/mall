package com.macro.mall.searchmodern.service;

import java.util.function.Consumer;

public interface ChatService {

    /**
     * Send a chat request with system prompt and user message.
     * Returns the assistant's reply text.
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Send a streaming chat request and pass each generated text delta to tokenConsumer.
     * Returns the full assistant reply after the stream completes.
     */
    String streamChat(String systemPrompt, String userMessage, Consumer<String> tokenConsumer);
}
