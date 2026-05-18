package com.macro.mall.searchmodern.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.searchmodern.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final String apiUrl;
    private final String model;
    private final String apiKey;
    private final int dims;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public EmbeddingServiceImpl(
            @Value("${app.embedding.api-url}") String apiUrl,
            @Value("${app.embedding.model}") String model,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.dims:1536}") int dims,
            @Value("${app.embedding.timeout-ms:10000}") int timeoutMs) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.model = model;
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : null;
        this.dims = dims;
        this.timeoutMs = timeoutMs;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            String url = apiUrl + "/v1/embeddings";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null) {
                headers.setBearerAuth(apiKey);
            }

            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", texts
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Embedding API returned status: {}", response.getStatusCode());
                return zeroVectors(texts.size());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                log.error("Embedding API response missing 'data' field");
                return zeroVectors(texts.size());
            }

            List<float[]> vectors = new ArrayList<>(texts.size());
            for (int i = 0; i < data.size(); i++) {
                JsonNode embedding = data.get(i).get("embedding");
                float[] vec = new float[dims];
                for (int j = 0; j < embedding.size() && j < dims; j++) {
                    vec[j] = (float) embedding.get(j).asDouble();
                }
                vectors.add(vec);
            }
            return vectors;
        } catch (Exception e) {
            log.error("Failed to call embedding API, returning zero vectors", e);
            return zeroVectors(texts.size());
        }
    }

    @Override
    public int dims() {
        return dims;
    }

    private List<float[]> zeroVectors(int count) {
        List<float[]> vectors = new ArrayList<>(count);
        float[] zeroVec = new float[dims];
        for (int i = 0; i < count; i++) {
            vectors.add(zeroVec.clone());
        }
        return vectors;
    }
}
