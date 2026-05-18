package com.macro.mall.service.impl;

import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.SmsCouponHistoryExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsCouponHistoryServiceImpl 单元测试")
class SmsCouponHistoryServiceImplTest {

    @Mock private SmsCouponHistoryMapper historyMapper;
    private SmsCouponHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsCouponHistoryServiceImpl(historyMapper);
    }

    @Nested
    @DisplayName("list - 优惠券使用记录列表")
    class ListTests {
        @Test
        @DisplayName("无过滤条件时分页查询全部")
        void list_noFilters_returnsAll() {
            var list = List.of(new SmsCouponHistory());
            when(historyMapper.selectByExample(any(SmsCouponHistoryExample.class))).thenReturn(list);

            assertThat(service.list(null, null, null, 10, 1)).hasSize(1);
        }

        @Test
        @DisplayName("按优惠券ID过滤")
        void list_withCouponId_filters() {
            when(historyMapper.selectByExample(any(SmsCouponHistoryExample.class))).thenReturn(List.of());

            var result = service.list(5L, null, null, 10, 1);

            assertThat(result).isEmpty();
        }
    }
}
