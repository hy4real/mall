package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPortalOrderReturnApplyServiceImpl 单元测试")
class OmsPortalOrderReturnApplyServiceImplTest {

    private OmsOrderReturnApplyMapper returnApplyMapper;
    private OmsPortalOrderReturnApplyServiceImpl service;

    @BeforeEach
    void setUp() {
        returnApplyMapper = mock(OmsOrderReturnApplyMapper.class);
        service = new OmsPortalOrderReturnApplyServiceImpl(returnApplyMapper);
    }

    @Test
    @DisplayName("创建退货申请：属性拷贝后设置状态和创建时间")
    void create_validParam_insertsWithStatus0() {
        OmsOrderReturnApplyParam param = new OmsOrderReturnApplyParam(
                1L, 2L, "SN001", "user1",
                "张三", "13800000000", "pic.jpg", "商品A", "品牌B",
                "颜色：红色", 1, null, null, "质量问题", "描述", null
        );
        when(returnApplyMapper.insert(any())).thenReturn(1);

        int result = service.create(param);

        assertThat(result).isEqualTo(1);
        ArgumentCaptor<OmsOrderReturnApply> captor = ArgumentCaptor.forClass(OmsOrderReturnApply.class);
        verify(returnApplyMapper).insert(captor.capture());
        OmsOrderReturnApply apply = captor.getValue();
        assertThat(apply.getStatus()).isEqualTo(0);
        assertThat(apply.getCreateTime()).isNotNull();
        assertThat(apply.getOrderSn()).isEqualTo("SN001");
    }
}
