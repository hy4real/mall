package com.macro.mall.searchpg.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductEmbedding {
    private Long id;
    private Long productId;
    private String name;
    private String description;
    private double[] embedding;
    private Integer dimensions;
    private String category;
    private String brand;
}