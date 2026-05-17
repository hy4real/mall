package com.macro.mall.dto;

import com.macro.mall.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 添加更新商品分类的参数
 * Created by macro on 2018/4/26.
 */
public record PmsProductCategoryParam(
    @Schema(description = "父分类的编号") Long parentId,
    @NotEmpty @Schema(description = "商品分类名称", requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(description = "分类单位") String productUnit,
    @FlagValidator(value = {"0","1"}, message = "状态只能为0或1") @Schema(description = "是否在导航栏显示") Integer navStatus,
    @FlagValidator(value = {"0","1"}, message = "状态只能为0或1") @Schema(description = "是否进行显示") Integer showStatus,
    @Min(value = 0) @Schema(description = "排序") Integer sort,
    @Schema(description = "图标") String icon,
    @Schema(description = "关键字") String keywords,
    @Schema(description = "描述") String description,
    @Schema(description = "商品相关筛选属性集合") List<Long> productAttributeIdList
) {}
