package com.macro.mall.dto;

import com.macro.mall.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 创建和修改商品的请求参数
 * Created by macro on 2018/4/26.
 */
@Data
@EqualsAndHashCode
public class PmsProductParam extends PmsProduct{
    @Schema(description = "商品阶梯价格设置")
    private List<PmsProductLadder> productLadderList;
    @Schema(description = "商品满减价格设置")
    private List<PmsProductFullReduction> productFullReductionList;
    @Schema(description = "商品会员价格设置")
    private List<PmsMemberPrice> memberPriceList;
    @Schema(description = "商品的sku库存信息")
    private List<PmsSkuStock> skuStockList;
    @Schema(description = "商品参数及自定义规格属性")
    private List<PmsProductAttributeValue> productAttributeValueList;
    @Schema(description = "专题和商品关系")
    private List<CmsSubjectProductRelation> subjectProductRelationList;
    @Schema(description = "优选专区和商品的关系")
    private List<CmsPrefrenceAreaProductRelation> prefrenceAreaProductRelationList;
}
