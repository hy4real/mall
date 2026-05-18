package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.GenerateOrderResult;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.service.OmsPortalOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPortalOrderController 单元测试")
class OmsPortalOrderControllerTest {

    @Mock
    private OmsPortalOrderService portalOrderService;

    @InjectMocks
    private OmsPortalOrderController controller;

    @Test
    @DisplayName("generateOrder 正常转发并返回成功结果")
    void generateOrder_success() {
        OrderParam orderParam = new OrderParam(1L, 2L, 3, 4, List.of(5L, 6L));
        OmsOrder order = new OmsOrder();
        order.setId(7L);
        OmsOrderItem item = new OmsOrderItem();
        item.setId(8L);
        GenerateOrderResult result = new GenerateOrderResult(order, List.of(item));
        when(portalOrderService.generateOrder(orderParam)).thenReturn(result);

        CommonResult response = controller.generateOrder(orderParam);

        assertThat(response.getCode()).isEqualTo(200L);
        assertThat(response.getMessage()).isEqualTo("下单成功");
        assertThat(response.getData()).isSameAs(result);

        ArgumentCaptor<OrderParam> captor = ArgumentCaptor.forClass(OrderParam.class);
        verify(portalOrderService).generateOrder(captor.capture());
        assertThat(captor.getValue()).isEqualTo(orderParam);
    }
}
