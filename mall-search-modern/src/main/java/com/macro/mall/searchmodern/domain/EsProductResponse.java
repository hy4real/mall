package com.macro.mall.searchmodern.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;

public record EsProductResponse(
        Long id,
        String productSn,
        Long brandId,
        String brandName,
        Long productCategoryId,
        String productCategoryName,
        String pic,
        String name,
        String subTitle,
        String keywords,
        BigDecimal price,
        Integer sale,
        Integer newStatus,
        Integer recommandStatus,
        Integer stock,
        Integer promotionType,
        Integer sort,
        List<EsProductAttributeValue> attrValueList) {

    public static EsProductResponse from(EsProduct product) {
        if (product == null) {
            return null;
        }
        return new EsProductResponse(
                product.getId(),
                product.getProductSn(),
                product.getBrandId(),
                product.getBrandName(),
                product.getProductCategoryId(),
                product.getProductCategoryName(),
                product.getPic(),
                product.getName(),
                product.getSubTitle(),
                product.getKeywords(),
                product.getPrice(),
                product.getSale(),
                product.getNewStatus(),
                product.getRecommandStatus(),
                product.getStock(),
                product.getPromotionType(),
                product.getSort(),
                product.getAttrValueList());
    }

    public static Page<EsProductResponse> fromPage(Page<EsProduct> page) {
        List<EsProductResponse> content = page.getContent().stream()
                .map(EsProductResponse::from)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }
}
