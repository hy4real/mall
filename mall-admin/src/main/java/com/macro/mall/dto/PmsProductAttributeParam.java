package com.macro.mall.dto;

import com.macro.mall.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;

/**
 * 商品属性参数
 * Created by macro on 2018/4/26.
 */
public record PmsProductAttributeParam(
    @NotEmpty @Schema(description = "属性分类ID") Long productAttributeCategoryId,
    @NotEmpty @Schema(description = "属性名称") String name,
    @FlagValidator({"0","1","2"}) @Schema(description = "属性选择类型：0->唯一；1->单选；2->多选") Integer selectType,
    @FlagValidator({"0","1"}) @Schema(description = "属性录入方式：0->手工录入；1->从列表中选取") Integer inputType,
    @Schema(description = "可选值列表，以逗号隔开") String inputList,
    Integer sort,
    @Schema(description = "分类筛选样式：0->普通；1->颜色") @FlagValidator({"0","1"}) Integer filterType,
    @Schema(description = "检索类型；0->不需要进行检索；1->关键字检索；2->范围检索") @FlagValidator({"0","1","2"}) Integer searchType,
    @Schema(description = "相同属性商品是否关联；0->不关联；1->关联") @FlagValidator({"0","1"}) Integer relatedStatus,
    @Schema(description = "是否支持手动新增；0->不支持；1->支持") @FlagValidator({"0","1"}) Integer handAddStatus,
    @Schema(description = "属性的类型；0->规格；1->参数") @FlagValidator({"0","1"}) Integer type
) {}
