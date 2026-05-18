package com.macro.mall.service.impl;

import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionExample;
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
@DisplayName("SmsFlashPromotionServiceImpl 单元测试")
class SmsFlashPromotionServiceImplTest {

    @Mock private SmsFlashPromotionMapper flashPromotionMapper;
    @Captor private ArgumentCaptor<SmsFlashPromotion> captor;
    private SmsFlashPromotionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsFlashPromotionServiceImpl(flashPromotionMapper);
    }

    @Nested
    @DisplayName("create - 创建限时购活动")
    class CreateTests {
        @Test
        @DisplayName("设置创建时间后插入")
        void create_setsCreateTime() {
            var promotion = new SmsFlashPromotion();
            when(flashPromotionMapper.insert(promotion)).thenReturn(1);

            int count = service.create(promotion);

            assertThat(count).isEqualTo(1);
            assertThat(promotion.getCreateTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("update - 更新限时购活动")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsId() {
            var promotion = new SmsFlashPromotion();
            when(flashPromotionMapper.updateByPrimaryKey(promotion)).thenReturn(1);

            int count = service.update(3L, promotion);

            assertThat(count).isEqualTo(1);
            assertThat(promotion.getId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("delete - 删除限时购活动")
    class DeleteTests {
        @Test
        @DisplayName("委托 mapper 删除")
        void delete_delegates() {
            when(flashPromotionMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            assertThat(service.delete(5L)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateStatus - 修改活动状态")
    class UpdateStatusTests {
        @Test
        @DisplayName("按 ID 更新状态字段")
        void updateStatus_setsStatus() {
            when(flashPromotionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.updateStatus(4L, 1);

            assertThat(count).isEqualTo(1);
            verify(flashPromotionMapper).updateByPrimaryKeySelective(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(4L);
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个活动")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new SmsFlashPromotion();
            when(flashPromotionMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("list - 分页查询活动列表")
    class ListTests {
        @Test
        @DisplayName("无关键词时分页查询全部")
        void list_noKeyword_returnsAll() {
            var list = List.of(new SmsFlashPromotion());
            when(flashPromotionMapper.selectByExample(any(SmsFlashPromotionExample.class))).thenReturn(list);

            assertThat(service.list(null, 10, 1)).hasSize(1);
        }
    }
}
