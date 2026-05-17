package com.macro.mall.dto;

import com.macro.mall.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

/**
 * 品牌请求参数
 * Created by macro on 2018/4/26.
 */
public record PmsBrandParam(
    @NotEmpty @Schema(description = "品牌名称", requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(description = "品牌首字母") String firstLetter,
    @Min(value = 0) @Schema(description = "排序字段") Integer sort,
    @FlagValidator(value = {"0","1"}, message = "厂家状态不正确") @Schema(description = "是否为厂家制造商") Integer factoryStatus,
    @FlagValidator(value = {"0","1"}, message = "显示状态不正确") @Schema(description = "是否进行显示") Integer showStatus,
    @NotEmpty @Schema(description = "品牌logo", requiredMode = Schema.RequiredMode.REQUIRED) String logo,
    @Schema(description = "品牌大图") String bigPic,
    @Schema(description = "品牌故事") String brandStory
) {}
