package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订单发货参数
 * Created by macro on 2018/10/12.
 */
public record OmsOrderDeliveryParam(
    @Schema(description = "订单id") Long orderId,
    @Schema(description = "物流公司") String deliveryCompany,
    @Schema(description = "物流单号") String deliverySn
) {}
