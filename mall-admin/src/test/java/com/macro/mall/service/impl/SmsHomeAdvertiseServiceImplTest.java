package com.macro.mall.service.impl;

import com.macro.mall.mapper.SmsHomeAdvertiseMapper;
import com.macro.mall.model.SmsHomeAdvertise;
import com.macro.mall.model.SmsHomeAdvertiseExample;
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
@DisplayName("SmsHomeAdvertiseServiceImpl 单元测试")
class SmsHomeAdvertiseServiceImplTest {

    @Mock private SmsHomeAdvertiseMapper advertiseMapper;
    @Captor private ArgumentCaptor<SmsHomeAdvertise> captor;
    private SmsHomeAdvertiseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsHomeAdvertiseServiceImpl(advertiseMapper);
    }

    @Nested
    @DisplayName("create - 创建广告")
    class CreateTests {
        @Test
        @DisplayName("初始化点击和订单数为0后插入")
        void create_setsDefaults() {
            var advertise = new SmsHomeAdvertise();
            when(advertiseMapper.insert(advertise)).thenReturn(1);

            int count = service.create(advertise);

            assertThat(count).isEqualTo(1);
            assertThat(advertise.getClickCount()).isEqualTo(0);
            assertThat(advertise.getOrderCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("delete - 批量删除广告")
    class DeleteTests {
        @Test
        @DisplayName("按 ID 列表批量删除")
        void delete_batch_delegates() {
            when(advertiseMapper.deleteByExample(any(SmsHomeAdvertiseExample.class))).thenReturn(3);

            assertThat(service.delete(List.of(1L, 2L, 3L))).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("updateStatus - 修改广告状态")
    class UpdateStatusTests {
        @Test
        @DisplayName("按 ID 更新状态")
        void updateStatus_setsStatus() {
            when(advertiseMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.updateStatus(4L, 1);

            assertThat(count).isEqualTo(1);
            verify(advertiseMapper).updateByPrimaryKeySelective(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个广告")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new SmsHomeAdvertise();
            when(advertiseMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("update - 更新广告")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后按主键更新")
        void update_setsId() {
            var advertise = new SmsHomeAdvertise();
            when(advertiseMapper.updateByPrimaryKeySelective(advertise)).thenReturn(1);

            service.update(3L, advertise);

            assertThat(advertise.getId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("list - 分页查询广告列表")
    class ListTests {
        @Test
        @DisplayName("无过滤条件时分页查询")
        void list_noFilters_returnsAll() {
            var list = List.of(new SmsHomeAdvertise());
            when(advertiseMapper.selectByExample(any(SmsHomeAdvertiseExample.class))).thenReturn(list);

            assertThat(service.list(null, null, null, 10, 1)).hasSize(1);
        }
    }
}
