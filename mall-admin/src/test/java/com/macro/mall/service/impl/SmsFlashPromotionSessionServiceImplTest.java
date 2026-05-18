package com.macro.mall.service.impl;

import com.macro.mall.dto.SmsFlashPromotionSessionDetail;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.model.SmsFlashPromotionSessionExample;
import com.macro.mall.service.SmsFlashPromotionProductRelationService;
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
@DisplayName("SmsFlashPromotionSessionServiceImpl 单元测试")
class SmsFlashPromotionSessionServiceImplTest {

    @Mock private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Mock private SmsFlashPromotionProductRelationService relationService;
    @Captor private ArgumentCaptor<SmsFlashPromotionSession> captor;
    private SmsFlashPromotionSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsFlashPromotionSessionServiceImpl(promotionSessionMapper, relationService);
    }

    @Nested
    @DisplayName("create - 创建场次")
    class CreateTests {
        @Test
        @DisplayName("设置创建时间后插入")
        void create_setsCreateTime() {
            var session = new SmsFlashPromotionSession();
            when(promotionSessionMapper.insert(session)).thenReturn(1);

            int count = service.create(session);

            assertThat(count).isEqualTo(1);
            assertThat(session.getCreateTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("update - 更新场次")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsId() {
            var session = new SmsFlashPromotionSession();
            when(promotionSessionMapper.updateByPrimaryKey(session)).thenReturn(1);

            service.update(3L, session);
            assertThat(session.getId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("updateStatus - 修改场次状态")
    class UpdateStatusTests {
        @Test
        @DisplayName("按 ID 更新状态字段")
        void updateStatus_setsStatus() {
            when(promotionSessionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.updateStatus(4L, 1);

            assertThat(count).isEqualTo(1);
            verify(promotionSessionMapper).updateByPrimaryKeySelective(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete - 删除场次")
    class DeleteTests {
        @Test
        @DisplayName("委托 mapper 删除")
        void delete_delegates() {
            when(promotionSessionMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            assertThat(service.delete(5L)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个场次")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new SmsFlashPromotionSession();
            when(promotionSessionMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("list - 获取全部场次")
    class ListTests {
        @Test
        @DisplayName("查询全部")
        void list_delegates() {
            var list = List.of(new SmsFlashPromotionSession());
            when(promotionSessionMapper.selectByExample(any(SmsFlashPromotionSessionExample.class))).thenReturn(list);

            assertThat(service.list()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("selectList - 查询场次详情（含商品数量）")
    class SelectListTests {
        @Test
        @DisplayName("返回带商品计数的场次详情")
        void selectList_returnsDetails() {
            var session = new SmsFlashPromotionSession();
            session.setId(10L);
            session.setName("10点场");
            session.setStatus(1);
            var sessions = List.of(session);
            when(promotionSessionMapper.selectByExample(any())).thenReturn(sessions);
            when(relationService.getCount(1L, 10L)).thenReturn(5L);

            List<SmsFlashPromotionSessionDetail> result = service.selectList(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductCount()).isEqualTo(5L);
        }
    }
}
