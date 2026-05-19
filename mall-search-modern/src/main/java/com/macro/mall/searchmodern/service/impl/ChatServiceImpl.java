package com.macro.mall.searchmodern.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.searchmodern.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final String apiUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public ChatServiceImpl(
            @Value("${app.chat.api-url}") String apiUrl,
            @Value("${app.chat.model}") String model,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.chat.timeout-ms:30000}") int timeoutMs) {
        this(apiUrl, model, apiKey, timeoutMs, createRestTemplate(timeoutMs));
    }

    ChatServiceImpl(String apiUrl, String model, String apiKey, int timeoutMs, RestTemplate restTemplate) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.model = model;
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : null;
        this.timeoutMs = timeoutMs;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
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

    @Override
    public String streamChat(String systemPrompt, String userMessage, Consumer<String> tokenConsumer) {
        try {
            String url = apiUrl + "/v1/chat/completions";
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            );

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.3,
                    "max_tokens", 512,
                    "stream", true
            );

            return restTemplate.execute(url, HttpMethod.POST, request -> {
                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                if (apiKey != null) {
                    request.getHeaders().setBearerAuth(apiKey);
                }
                objectMapper.writeValue(request.getBody(), body);
            }, response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("Chat API returned status: " + response.getStatusCode());
                }

                StringBuilder answer = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String token = parseStreamToken(line);
                        if (token == null || token.isEmpty()) {
                            continue;
                        }
                        answer.append(token);
                        tokenConsumer.accept(token);
                    }
                }
                return answer.toString();
            });
        } catch (Exception e) {
            log.error("Failed to call streaming Chat API", e);
            throw new IllegalStateException("AI streaming service call failed", e);
        }
    }

    private String parseStreamToken(String line) throws IOException {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return null;
        }

        String data = line.substring("data:".length()).trim();
        if (data.isBlank() || "[DONE]".equals(data)) {
            return null;
        }

        JsonNode root = objectMapper.readTree(data);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }

        JsonNode choice = choices.get(0);
        JsonNode delta = choice.get("delta");
        if (delta != null && delta.has("content")) {
            return delta.get("content").asText();
        }

        JsonNode message = choice.get("message");
        if (message != null && message.has("content")) {
            return message.get("content").asText();
        }
        return null;
    }
}
