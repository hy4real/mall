package com.macro.mall.searchmodern.domain;

import java.util.List;

public record ProductRecommendation(
        EsProductResponse product,
        double score,
        List<String> reasonTags,
        List<String> reasons) {
}
