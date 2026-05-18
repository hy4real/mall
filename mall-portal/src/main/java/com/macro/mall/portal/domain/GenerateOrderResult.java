package com.macro.mall.portal.domain;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;

import java.util.List;

public record GenerateOrderResult(OmsOrder order, List<OmsOrderItem> orderItemList) {
}
