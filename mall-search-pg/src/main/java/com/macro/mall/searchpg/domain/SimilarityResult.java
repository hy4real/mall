package com.macro.mall.searchpg.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimilarityResult {
    private Long productId;
    private String name;
    private String description;
    private double similarity;
}