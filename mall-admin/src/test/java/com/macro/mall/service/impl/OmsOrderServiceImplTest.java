package com.macro.mall.service.impl;

import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dao.OmsOrderOperateHistoryDao;
import com.macro.mall.dto.OmsMoneyInfoParam;
import com.macro.mall.dto.OmsOrderDeliveryParam;
import com.macro.mall.dto.OmsOrderDetail;
import com.macro.mall.dto.OmsOrderQueryParam;
import com.macro.mall.dto.OmsReceiverInfoParam;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderOperateHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderOperateHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsOrderServiceImpl 单元测试")
class OmsOrderServiceImplTest {

    @Mock
    private OmsOrderMapper orderMapper;
    @Mock
    private OmsOrderDao orderDao;
    @Mock
    private OmsOrderOperateHistoryDao orderOperateHistoryDao;
    @Mock
    private OmsOrderOperateHistoryMapper orderOperateHistoryMapper;

    @Captor
    private ArgumentCaptor<List<OmsOrderOperateHistory>> historyListCaptor;
    @Captor
    private ArgumentCaptor<OmsOrderOperateHistory> historyCaptor;
    @Captor
    private ArgumentCaptor<OmsOrder> orderCaptor;
    @Captor
    private ArgumentCaptor<OmsOrderExample> exampleCaptor;

    private OmsOrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OmsOrderServiceImpl(
                orderMapper, orderDao, orderOperateHistoryDao, orderOperateHistoryMapper);
    }
    @Nested
    @DisplayName("list - 分页查询订单")
    class ListTests {
        @Test
        @DisplayName("委托 orderDao.getList 并传递查询参数")
        void list_delegatesToDao() {
            var queryParam = new OmsOrderQueryParam("202301010001", null, 1, null, null, null);
            var expected = List.of(new OmsOrder());
            when(orderDao.getList(queryParam)).thenReturn(expected);

            List<OmsOrder> result = orderService.list(queryParam, 10, 1);

            assertThat(result).isEqualTo(expected);
            verify(orderDao).getList(queryParam);
        }
    }

    @Nested
    @DisplayName("delivery - 批量发货")
    class DeliveryTests {
        @Test
        @DisplayName("调用 dao 发货并插入操作记录")
        void delivery_updatesAndInsertsHistory() {
            var params = List.of(
                    new OmsOrderDeliveryParam(1L, "顺丰", "SF001"),
                    new OmsOrderDeliveryParam(2L, "圆通", "YT002"));
            when(orderDao.delivery(params)).thenReturn(2);

            int count = orderService.delivery(params);

            assertThat(count).isEqualTo(2);
            verify(orderOperateHistoryDao).insertList(historyListCaptor.capture());
            List<OmsOrderOperateHistory> histories = historyListCaptor.getValue();
            assertThat(histories).hasSize(2);
            assertThat(histories.get(0).getOrderId()).isEqualTo(1L);
            assertThat(histories.get(0).getOrderStatus()).isEqualTo(2);
            assertThat(histories.get(0).getNote()).isEqualTo("完成发货");
            assertThat(histories.get(1).getOrderId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("close - 关闭订单")
    class CloseTests {
        @Test
        @DisplayName("设置状态为4并插入操作记录")
        void close_setsStatusAndInsertsHistory() {
            var ids = List.of(10L, 20L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class)))
                    .thenReturn(2);

            int count = orderService.close(ids, "缺货");

            assertThat(count).isEqualTo(2);
            verify(orderMapper).updateByExampleSelective(orderCaptor.capture(), any(OmsOrderExample.class));
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(4);
            verify(orderOperateHistoryDao).insertList(historyListCaptor.capture());
            List<OmsOrderOperateHistory> histories = historyListCaptor.getValue();
            assertThat(histories).hasSize(2);
            assertThat(histories.get(0).getOrderStatus()).isEqualTo(4);
            assertThat(histories.get(0).getNote()).isEqualTo("订单关闭:缺货");
        }
    }
    @Nested
    @DisplayName("delete - 逻辑删除订单")
    class DeleteTests {
        @Test
        @DisplayName("设置 deleteStatus=1 并按 ID 过滤")
        void delete_setsDeleteStatus() {
            var ids = List.of(5L, 6L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class)))
                    .thenReturn(2);

            int count = orderService.delete(ids);

            assertThat(count).isEqualTo(2);
            verify(orderMapper).updateByExampleSelective(orderCaptor.capture(), any(OmsOrderExample.class));
            assertThat(orderCaptor.getValue().getDeleteStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("detail - 获取订单详情")
    class DetailTests {
        @Test
        @DisplayName("委托 orderDao.getDetail")
        void detail_delegatesToDao() {
            var expected = new OmsOrderDetail();
            when(orderDao.getDetail(99L)).thenReturn(expected);

            OmsOrderDetail result = orderService.detail(99L);

            assertThat(result).isSameAs(expected);
            verify(orderDao).getDetail(99L);
        }
    }

    @Nested
    @DisplayName("updateReceiverInfo - 修改收货人信息")
    class UpdateReceiverInfoTests {
        @Test
        @DisplayName("更新订单字段并插入操作记录")
        void updateReceiverInfo_updatesOrderAndInsertsHistory() {
            var param = new OmsReceiverInfoParam(
                    1L, "张三", "13800138000", "310000",
                    "浦东新区XX路", "上海", "上海", "浦东新区", 1);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);

            int count = orderService.updateReceiverInfo(param);

            assertThat(count).isEqualTo(1);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder captured = orderCaptor.getValue();
            assertThat(captured.getId()).isEqualTo(1L);
            assertThat(captured.getReceiverName()).isEqualTo("张三");
            assertThat(captured.getReceiverPhone()).isEqualTo("13800138000");
            assertThat(captured.getReceiverCity()).isEqualTo("上海");
            assertThat(captured.getModifyTime()).isNotNull();

            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            OmsOrderOperateHistory history = historyCaptor.getValue();
            assertThat(history.getOrderId()).isEqualTo(1L);
            assertThat(history.getOrderStatus()).isEqualTo(1);
            assertThat(history.getNote()).isEqualTo("修改收货人信息");
        }
    }

    @Nested
    @DisplayName("updateMoneyInfo - 修改费用信息")
    class UpdateMoneyInfoTests {
        @Test
        @DisplayName("更新运费和折扣并插入操作记录")
        void updateMoneyInfo_updatesAndInsertsHistory() {
            var param = new OmsMoneyInfoParam(1L, new BigDecimal("10.00"), new BigDecimal("5.00"), 2);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);

            int count = orderService.updateMoneyInfo(param);

            assertThat(count).isEqualTo(1);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder captured = orderCaptor.getValue();
            assertThat(captured.getFreightAmount()).isEqualByComparingTo("10.00");
            assertThat(captured.getDiscountAmount()).isEqualByComparingTo("5.00");

            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getNote()).isEqualTo("修改费用信息");
        }
    }

    @Nested
    @DisplayName("updateNote - 修改备注")
    class UpdateNoteTests {
        @Test
        @DisplayName("更新备注并插入操作记录")
        void updateNote_updatesAndInsertsHistory() {
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);

            int count = orderService.updateNote(7L, "加急处理", 3);

            assertThat(count).isEqualTo(1);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getNote()).isEqualTo("加急处理");
            assertThat(orderCaptor.getValue().getId()).isEqualTo(7L);

            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            OmsOrderOperateHistory history = historyCaptor.getValue();
            assertThat(history.getOrderStatus()).isEqualTo(3);
            assertThat(history.getNote()).isEqualTo("修改备注信息：加急处理");
        }
    }
}
