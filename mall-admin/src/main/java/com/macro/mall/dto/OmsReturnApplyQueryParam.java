package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订单退货申请查询参数
 * Created by macro on 2018/10/18.
 */
public record OmsReturnApplyQueryParam(
    @Schema(description = "服务单号") Long id,
    @Schema(description = "收货人姓名/号码") String receiverKeyword,
    @Schema(description = "申请状态：0->待处理；1->退货中；2->已完成；3->已拒绝") Integer status,
    @Schema(description = "申请时间") String createTime,
    @Schema(description = "处理人员") String handleMan,
    @Schema(description = "处理时间") String handleTime
) {}
