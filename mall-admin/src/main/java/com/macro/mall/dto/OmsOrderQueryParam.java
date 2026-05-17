package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订单查询参数
 * Created by macro on 2018/10/11.
 */
public record OmsOrderQueryParam(
    @Schema(description = "订单编号") String orderSn,
    @Schema(description = "收货人姓名/号码") String receiverKeyword,
    @Schema(description = "订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单") Integer status,
    @Schema(description = "订单类型：0->正常订单；1->秒杀订单") Integer orderType,
    @Schema(description = "订单来源：0->PC订单；1->app订单") Integer sourceType,
    @Schema(description = "订单提交时间") String createTime
) {}
