package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 商品查询参数
 * Created by macro on 2018/4/27.
 */
public record PmsProductQueryParam(
    @Schema(description = "上架状态") Integer publishStatus,
    @Schema(description = "审核状态") Integer verifyStatus,
    @Schema(description = "商品名称模糊关键字") String keyword,
    @Schema(description = "商品货号") String productSn,
    @Schema(description = "商品分类编号") Long productCategoryId,
    @Schema(description = "商品品牌编号") Long brandId
) {}
