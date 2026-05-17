package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 修改订单费用信息参数
 * Created by macro on 2018/10/29.
 */
public record OmsMoneyInfoParam(
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "运费金额") BigDecimal freightAmount,
    @Schema(description = "管理员后台调整订单所使用的折扣金额") BigDecimal discountAmount,
    @Schema(description = "订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单") Integer status
) {}
