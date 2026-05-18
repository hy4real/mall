package com.macro.mall.service.impl;

import com.macro.mall.mapper.OmsOrderReturnReasonMapper;
import com.macro.mall.model.OmsOrderReturnReason;
import com.macro.mall.model.OmsOrderReturnReasonExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsOrderReturnReasonServiceImpl 单元测试")
class OmsOrderReturnReasonServiceImplTest {

    @Mock private OmsOrderReturnReasonMapper returnReasonMapper;
    @Captor private ArgumentCaptor<OmsOrderReturnReason> captor;
    private OmsOrderReturnReasonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OmsOrderReturnReasonServiceImpl(returnReasonMapper);
    }

    @Nested
    @DisplayName("create - 创建退货原因")
    class CreateTests {
        @Test
        @DisplayName("设置创建时间后插入")
        void create_setsCreateTime() {
            var reason = new OmsOrderReturnReason();
            when(returnReasonMapper.insert(reason)).thenReturn(1);

            int count = service.create(reason);

            assertThat(count).isEqualTo(1);
            assertThat(reason.getCreateTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("update - 更新退货原因")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后调用更新")
        void update_setsIdAndCallsMapper() {
            var reason = new OmsOrderReturnReason();
            when(returnReasonMapper.updateByPrimaryKey(any(OmsOrderReturnReason.class))).thenReturn(1);

            int count = service.update(5L, reason);

            assertThat(count).isEqualTo(1);
            assertThat(reason.getId()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("delete - 批量删除")
    class DeleteTests {
        @Test
        @DisplayName("按 ID 列表批量删除")
        void delete_batch() {
            when(returnReasonMapper.deleteByExample(any(OmsOrderReturnReasonExample.class))).thenReturn(3);

            assertThat(service.delete(List.of(1L, 2L, 3L))).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("list - 分页查询")
    class ListTests {
        @Test
        @DisplayName("委托 mapper 分页查询")
        void list_delegates() {
            when(returnReasonMapper.selectByExample(any(OmsOrderReturnReasonExample.class))).thenReturn(List.of());

            assertThat(service.list(10, 1)).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateStatus - 修改状态")
    class UpdateStatusTests {
        @Test
        @DisplayName("状态为0或1时更新成功")
        void updateStatus_valid_updates() {
            when(returnReasonMapper.updateByExampleSelective(any(), any())).thenReturn(2);

            int count = service.updateStatus(List.of(1L, 2L), 0);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("状态非法时返回0")
        void updateStatus_invalid_returnsZero() {
            int count = service.updateStatus(List.of(1L), 2);

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个退货原因")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new OmsOrderReturnReason();
            when(returnReasonMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }
}
