package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.dao.AiAnalysisDao;
import com.macro.mall.searchmodern.domain.OrderAnomalyReport;
import com.macro.mall.searchmodern.domain.OrderInteraction;
import com.macro.mall.searchmodern.domain.OrderSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderAnomalyServiceImpl 单元测试")
class OrderAnomalyServiceImplTest {

    @Test
    @DisplayName("识别同一会员短时间高频下单和金额偏离")
    void detectOrderAnomalies_detectsBurstAndAmountOutlier() {
        LocalDateTime base = LocalDateTime.of(2023, 5, 11, 15, 0);
        FakeAiAnalysisDao dao = new FakeAiAnalysisDao(List.of(
                order(1L, 11L, "1001", base, "100.00", 3),
                order(2L, 11L, "1002", base.plusMinutes(5), "120.00", 3),
                order(3L, 11L, "1003", base.plusMinutes(10), "110.00", 3),
                order(4L, 11L, "1004", base.plusMinutes(15), "130.00", 3),
                order(5L, 11L, "1005", base.plusMinutes(20), "15000.00", 3),
                order(6L, 11L, "1006", base.plusMinutes(25), "140.00", 3),
                order(7L, 12L, "2001", base.plusDays(1), "300.00", 3)
        ));
        OrderAnomalyServiceImpl service = new OrderAnomalyServiceImpl(dao);

        OrderAnomalyReport report = service.detectOrderAnomalies(30);

        assertThat(report.totalOrders()).isEqualTo(7);
        assertThat(report.anomalyCount()).isGreaterThanOrEqualTo(6);
        assertThat(report.summary()).containsEntry("ORDER_BURST", 6L);
        assertThat(report.anomalies().getFirst().reasonTags()).contains("AMOUNT_OUTLIER");
        assertThat(report.anomalies()).anySatisfy(anomaly ->
                assertThat(anomaly.features()).containsEntry("rollingHourOrderCount", 6));
    }

    private static OrderSignal order(Long id, Long memberId, String orderSn,
                                     LocalDateTime createTime, String amount, Integer status) {
        OrderSignal order = new OrderSignal();
        order.setId(id);
        order.setMemberId(memberId);
        order.setOrderSn(orderSn);
        order.setMemberUsername("member-" + memberId);
        order.setCreateTime(createTime);
        order.setPayAmount(new BigDecimal(amount));
        order.setTotalAmount(new BigDecimal(amount));
        order.setStatus(status);
        return order;
    }

    private static class FakeAiAnalysisDao implements AiAnalysisDao {
        private final List<OrderSignal> orders;

        private FakeAiAnalysisDao(List<OrderSignal> orders) {
            this.orders = orders;
        }

        @Override
        public List<OrderInteraction> listOrderInteractions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OrderSignal> listOrdersInAnalysisWindow(Integer days) {
            return orders;
        }

        @Override
        public LocalDateTime getOrderAnalysisWindowEnd() {
            return orders.stream()
                    .map(OrderSignal::getCreateTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }
    }
}
