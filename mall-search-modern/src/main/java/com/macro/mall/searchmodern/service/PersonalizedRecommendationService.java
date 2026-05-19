package com.macro.mall.searchmodern.service;

import com.macro.mall.searchmodern.domain.ProductRecommendation;

import java.util.List;

public interface PersonalizedRecommendationService {
    List<ProductRecommendation> recommend(Long memberId, Integer size);
}
