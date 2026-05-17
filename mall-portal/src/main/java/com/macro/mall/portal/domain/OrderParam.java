package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 生成订单时传入的参数
 * Created by macro on 2018/8/30.
 */
public record OrderParam(
    @Schema(description = "收货地址ID") Long memberReceiveAddressId,
    @Schema(description = "优惠券ID") Long couponId,
    @Schema(description = "使用的积分数") Integer useIntegration,
    @Schema(description = "支付方式") Integer payType,
    @Schema(description = "被选中的购物车商品ID") List<Long> cartIds
) {
}
