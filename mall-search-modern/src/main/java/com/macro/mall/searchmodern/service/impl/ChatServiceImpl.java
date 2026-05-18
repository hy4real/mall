package com.macro.mall.searchmodern.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.searchmodern.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final String apiUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ChatServiceImpl(
            @Value("${app.chat.api-url}") String apiUrl,
            @Value("${app.chat.model}") String model,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.chat.timeout-ms:30000}") int timeoutMs) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.model = model;
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : null;
        this.timeoutMs = timeoutMs;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            String url = apiUrl + "/v1/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null) {
                headers.setBearerAuth(apiKey);
            }

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            );

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.3,
                    "max_tokens", 512
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Chat API returned status: {}", response.getStatusCode());
                return "抱歉，AI 服务暂时不可用。";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                log.error("Chat API response missing 'choices' field");
                return "抱歉，AI 服务返回异常。";
            }

            JsonNode message = choices.get(0).get("message");
            if (message == null) {
                return "抱歉，AI 服务返回异常。";
            }

            String content = message.get("content").asText();
            return content != null ? content : "抱歉，未能生成回答。";
        } catch (Exception e) {
            log.error("Failed to call Chat API", e);
            return "抱歉，AI 服务调用失败。";
        }
    }
}
