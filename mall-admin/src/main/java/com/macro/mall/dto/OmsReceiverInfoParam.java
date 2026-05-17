package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订单修改收货人信息参数
 * Created by macro on 2018/10/29.
 */
public record OmsReceiverInfoParam(
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "收货人姓名") String receiverName,
    @Schema(description = "收货人电话") String receiverPhone,
    @Schema(description = "收货人邮编") String receiverPostCode,
    @Schema(description = "详细地址") String receiverDetailAddress,
    @Schema(description = "省份/直辖市") String receiverProvince,
    @Schema(description = "城市") String receiverCity,
    @Schema(description = "区") String receiverRegion,
    @Schema(description = "订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单") Integer status
) {}
