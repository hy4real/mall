package com.macro.mall.searchmodern.service;

import com.macro.mall.searchmodern.domain.ProductRecommendation;

import java.util.List;
import java.util.Optional;

public interface RecommendationCache {
    Optional<List<ProductRecommendation>> get(Long memberId);

    void put(Long memberId, List<ProductRecommendation> recommendations);
}
