package com.macro.mall.searchpg.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HybridSearchResult {
    private Long productId;
    private String name;
    private String description;
    private String category;
    private String brand;
    private double similarity;
    private double textScore;
    private double hybridScore;
}
