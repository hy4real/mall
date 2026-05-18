package com.macro.mall.service.impl;

import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsOrderReturnApplyServiceImpl 单元测试")
class OmsOrderReturnApplyServiceImplTest {

    @Mock private OmsOrderReturnApplyDao returnApplyDao;
    @Mock private OmsOrderReturnApplyMapper returnApplyMapper;
    @Captor private ArgumentCaptor<OmsOrderReturnApply> applyCaptor;
    private OmsOrderReturnApplyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OmsOrderReturnApplyServiceImpl(returnApplyDao, returnApplyMapper);
    }

    @Nested
    @DisplayName("list - 分页查询退货申请")
    class ListTests {
        @Test
        @DisplayName("委托 DAO 分页查询")
        void list_delegates() {
            var param = new OmsReturnApplyQueryParam(null, null, null, null, null, null);
            var list = List.of(new OmsOrderReturnApply());
            when(returnApplyDao.getList(param)).thenReturn(list);

            assertThat(service.list(param, 10, 1)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("delete - 删除退货申请（仅已拒绝的）")
    class DeleteTests {
        @Test
        @DisplayName("只删除状态为3（已拒绝）的申请")
        void delete_onlyRejected() {
            when(returnApplyMapper.deleteByExample(any(OmsOrderReturnApplyExample.class))).thenReturn(2);

            int count = service.delete(List.of(1L, 2L));

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("updateStatus - 处理退货申请")
    class UpdateStatusTests {

        @Test
        @DisplayName("状态=1：确认退货，设置退货金额和收货地址")
        void updateStatus_confirmReturn() {
            var param = new OmsUpdateStatusParam(1L, 10L, new BigDecimal("99.00"),
                    "备注", "处理人", null, null, 1);
            when(returnApplyMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.updateStatus(1L, param);

            assertThat(count).isEqualTo(1);
            verify(returnApplyMapper).updateByPrimaryKeySelective(applyCaptor.capture());
            var apply = applyCaptor.getValue();
            assertThat(apply.getStatus()).isEqualTo(1);
            assertThat(apply.getReturnAmount()).isEqualByComparingTo("99.00");
            assertThat(apply.getCompanyAddressId()).isEqualTo(10L);
            assertThat(apply.getHandleTime()).isNotNull();
        }

        @Test
        @DisplayName("状态=2：完成退货，设置收货信息")
        void updateStatus_completeReturn() {
            var param = new OmsUpdateStatusParam(2L, null, null,
                    null, null, "收货备注", "收货人", 2);
            when(returnApplyMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.updateStatus(2L, param);

            assertThat(count).isEqualTo(1);
            verify(returnApplyMapper).updateByPrimaryKeySelective(applyCaptor.capture());
            var apply = applyCaptor.getValue();
            assertThat(apply.getStatus()).isEqualTo(2);
            assertThat(apply.getReceiveMan()).isEqualTo("收货人");
            assertThat(apply.getReceiveTime()).isNotNull();
        }

        @Test
        @DisplayName("状态=3：拒绝退货，设置处理信息")
        void updateStatus_rejectReturn() {
            var param = new OmsUpdateStatusParam(3L, null, null,
                    "拒绝原因", "处理人", null, null, 3);
            when(returnApplyMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.updateStatus(3L, param);

            assertThat(count).isEqualTo(1);
            verify(returnApplyMapper).updateByPrimaryKeySelective(applyCaptor.capture());
            var apply = applyCaptor.getValue();
            assertThat(apply.getStatus()).isEqualTo(3);
            assertThat(apply.getHandleMan()).isEqualTo("处理人");
            assertThat(apply.getHandleNote()).isEqualTo("拒绝原因");
            assertThat(apply.getHandleTime()).isNotNull();
        }

        @Test
        @DisplayName("非法状态返回0")
        void updateStatus_invalid_returnsZero() {
            var param = new OmsUpdateStatusParam(4L, null, null,
                    null, null, null, null, 99);

            int count = service.updateStatus(4L, param);

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getItem - 获取退货申请详情")
    class GetItemTests {
        @Test
        @DisplayName("委托 DAO 查询")
        void getItem_delegates() {
            var result = new OmsOrderReturnApplyResult();
            when(returnApplyDao.getDetail(7L)).thenReturn(result);

            assertThat(service.getItem(7L)).isSameAs(result);
        }
    }
}
