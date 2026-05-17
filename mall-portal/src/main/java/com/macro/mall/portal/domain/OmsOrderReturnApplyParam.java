package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 退货申请请求参数
 * Created by macro on 2018/10/17.
 */
@Getter
@Setter
public class OmsOrderReturnApplyParam {
    @Schema(description = "订单id")
    private Long orderId;
    @Schema(description = "退货商品id")
    private Long productId;
    @Schema(description = "订单编号")
    private String orderSn;
    @Schema(description = "会员用户名")
    private String memberUsername;
    @Schema(description = "退货人姓名")
    private String returnName;
    @Schema(description = "退货人电话")
    private String returnPhone;
    @Schema(description = "商品图片")
    private String productPic;
    @Schema(description = "商品名称")
    private String productName;
    @Schema(description = "商品品牌")
    private String productBrand;
    @Schema(description = "商品销售属性：颜色：红色；尺码：xl;")
    private String productAttr;
    @Schema(description = "退货数量")
    private Integer productCount;
    @Schema(description = "商品单价")
    private BigDecimal productPrice;
    @Schema(description = "商品实际支付单价")
    private BigDecimal productRealPrice;
    @Schema(description = "原因")
    private String reason;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "凭证图片，以逗号隔开")
    private String proofPics;

}
