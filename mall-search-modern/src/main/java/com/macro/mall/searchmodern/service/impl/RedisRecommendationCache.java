package com.macro.mall.searchmodern.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.searchmodern.domain.ProductRecommendation;
import com.macro.mall.searchmodern.service.RecommendationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class RedisRecommendationCache implements RecommendationCache {
    private static final Logger log = LoggerFactory.getLogger(RedisRecommendationCache.class);
    private static final Duration TTL = Duration.ofHours(1);
    private static final TypeReference<List<ProductRecommendation>> RECOMMENDATION_LIST =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRecommendationCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<List<ProductRecommendation>> get(Long memberId) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey(memberId));
            if (cached == null || cached.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, RECOMMENDATION_LIST));
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Recommendation cache read skipped for memberId={}: {}", memberId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long memberId, List<ProductRecommendation> recommendations) {
        try {
            String value = objectMapper.writeValueAsString(recommendations);
            redisTemplate.opsForValue().set(cacheKey(memberId), value, TTL);
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Recommendation cache write skipped for memberId={}: {}", memberId, e.getMessage());
        }
    }

    private static String cacheKey(Long memberId) {
        return "recommend:" + memberId;
    }
}
