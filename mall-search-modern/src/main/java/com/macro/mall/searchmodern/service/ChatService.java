package com.macro.mall.searchmodern.service;

public interface ChatService {

    /**
     * Send a chat request with system prompt and user message.
     * Returns the assistant's reply text.
     */
    String chat(String systemPrompt, String userMessage);
}
