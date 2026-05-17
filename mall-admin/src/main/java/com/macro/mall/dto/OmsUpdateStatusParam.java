package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 确认收货请求参数
 * Created by macro on 2018/10/18.
 */
public record OmsUpdateStatusParam(
    @Schema(description = "服务单号") Long id,
    @Schema(description = "收货地址关联id") Long companyAddressId,
    @Schema(description = "确认退款金额") BigDecimal returnAmount,
    @Schema(description = "处理备注") String handleNote,
    @Schema(description = "处理人") String handleMan,
    @Schema(description = "收货备注") String receiveNote,
    @Schema(description = "收货人") String receiveMan,
    @Schema(description = "申请状态：1->退货中；2->已完成；3->已拒绝") Integer status
) {}
