package com.macro.mall.service.impl;

import com.macro.mall.mapper.SmsHomeRecommendProductMapper;
import com.macro.mall.model.SmsHomeRecommendProduct;
import com.macro.mall.model.SmsHomeRecommendProductExample;
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
@DisplayName("SmsHomeRecommendProductServiceImpl 单元测试")
class SmsHomeRecommendProductServiceImplTest {

    @Mock private SmsHomeRecommendProductMapper recommendProductMapper;
    private SmsHomeRecommendProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsHomeRecommendProductServiceImpl(recommendProductMapper);
    }

    @Nested
    @DisplayName("create - 批量添加人气推荐")
    class CreateTests {
        @Test
        @DisplayName("设置默认推荐状态和排序后逐条插入")
        void create_setsDefaults() {
            var p1 = new SmsHomeRecommendProduct();
            when(recommendProductMapper.insert(any())).thenReturn(1);

            int count = service.create(List.of(p1));

            assertThat(count).isEqualTo(1);
            assertThat(p1.getRecommendStatus()).isEqualTo(1);
            assertThat(p1.getSort()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("updateSort - 修改排序")
    class UpdateSortTests {
        @Test
        @DisplayName("按 ID 更新")
        void updateSort_delegates() {
            when(recommendProductMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            assertThat(service.updateSort(3L, 2)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete - 批量删除")
    class DeleteTests {
        @Test
        @DisplayName("按 ID 列表批量删除")
        void delete_batch() {
            when(recommendProductMapper.deleteByExample(any(SmsHomeRecommendProductExample.class))).thenReturn(3);

            assertThat(service.delete(List.of(1L, 2L, 3L))).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("updateRecommendStatus - 批量修改推荐状态")
    class UpdateRecommendStatusTests {
        @Test
        @DisplayName("批量更新")
        void updateRecommendStatus_batch() {
            when(recommendProductMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            assertThat(service.updateRecommendStatus(List.of(1L), 0)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("list - 分页查询")
    class ListTests {
        @Test
        @DisplayName("无过滤条件时查询")
        void list_noFilters() {
            when(recommendProductMapper.selectByExample(any(SmsHomeRecommendProductExample.class))).thenReturn(List.of());

            assertThat(service.list(null, null, 10, 1)).isEmpty();
        }
    }
}
