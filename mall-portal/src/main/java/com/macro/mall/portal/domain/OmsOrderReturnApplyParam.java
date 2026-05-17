package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 退货申请请求参数
 * Created by macro on 2018/10/17.
 */
public record OmsOrderReturnApplyParam(
    @Schema(description = "订单id") Long orderId,
    @Schema(description = "退货商品id") Long productId,
    @Schema(description = "订单编号") String orderSn,
    @Schema(description = "会员用户名") String memberUsername,
    @Schema(description = "退货人姓名") String returnName,
    @Schema(description = "退货人电话") String returnPhone,
    @Schema(description = "商品图片") String productPic,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "商品品牌") String productBrand,
    @Schema(description = "商品销售属性：颜色：红色；尺码：xl;") String productAttr,
    @Schema(description = "退货数量") Integer productCount,
    @Schema(description = "商品单价") BigDecimal productPrice,
    @Schema(description = "商品实际支付单价") BigDecimal productRealPrice,
    @Schema(description = "原因") String reason,
    @Schema(description = "描述") String description,
    @Schema(description = "凭证图片，以逗号隔开") String proofPics
) {
}
