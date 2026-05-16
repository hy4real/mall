package com.macro.mall.searchmodern.domain;

import java.util.List;

public record EsProductRelatedInfo(
        List<String> brandNames,
        List<String> productCategoryNames,
        List<ProductAttr> productAttrs
) {
    public record ProductAttr(Long attrId, String attrName, List<String> attrValues) {}
}
