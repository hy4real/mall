package com.macro.mall.service.impl;

import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.model.OmsOrderSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsOrderSettingServiceImpl 单元测试")
class OmsOrderSettingServiceImplTest {

    @Mock private OmsOrderSettingMapper orderSettingMapper;
    @Captor private ArgumentCaptor<OmsOrderSetting> settingCaptor;
    private OmsOrderSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OmsOrderSettingServiceImpl(orderSettingMapper);
    }

    @Nested
    @DisplayName("getItem - 获取订单设置")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new OmsOrderSetting();
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("update - 更新订单设置")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsIdAndUpdates() {
            var setting = new OmsOrderSetting();
            when(orderSettingMapper.updateByPrimaryKey(setting)).thenReturn(1);

            int count = service.update(5L, setting);

            assertThat(count).isEqualTo(1);
            assertThat(setting.getId()).isEqualTo(5L);
        }
    }
}
