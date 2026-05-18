package com.macro.mall.service.impl;

import com.macro.mall.mapper.SmsHomeBrandMapper;
import com.macro.mall.model.SmsHomeBrand;
import com.macro.mall.model.SmsHomeBrandExample;
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
@DisplayName("SmsHomeBrandServiceImpl 单元测试")
class SmsHomeBrandServiceImplTest {

    @Mock private SmsHomeBrandMapper homeBrandMapper;
    @Captor private ArgumentCaptor<SmsHomeBrandExample> exampleCaptor;
    private SmsHomeBrandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsHomeBrandServiceImpl(homeBrandMapper);
    }

    @Nested
    @DisplayName("create - 批量添加首页品牌")
    class CreateTests {
        @Test
        @DisplayName("设置默认推荐状态和排序后逐条插入")
        void create_setsDefaults() {
            var b1 = new SmsHomeBrand();
            var b2 = new SmsHomeBrand();
            when(homeBrandMapper.insert(any())).thenReturn(1);

            int count = service.create(List.of(b1, b2));

            assertThat(count).isEqualTo(2);
            assertThat(b1.getRecommendStatus()).isEqualTo(1);
            assertThat(b1.getSort()).isEqualTo(0);
            assertThat(b2.getRecommendStatus()).isEqualTo(1);
            assertThat(b2.getSort()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("updateSort - 修改排序")
    class UpdateSortTests {
        @Test
        @DisplayName("按 ID 更新排序值")
        void updateSort_setsSort() {
            when(homeBrandMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            assertThat(service.updateSort(3L, 5)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete - 批量删除")
    class DeleteTests {
        @Test
        @DisplayName("按 ID 列表批量删除")
        void delete_batch() {
            when(homeBrandMapper.deleteByExample(any(SmsHomeBrandExample.class))).thenReturn(2);

            assertThat(service.delete(List.of(1L, 2L))).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("updateRecommendStatus - 批量修改推荐状态")
    class UpdateRecommendStatusTests {
        @Test
        @DisplayName("按 ID 列表批量更新推荐状态")
        void updateRecommendStatus_batch() {
            when(homeBrandMapper.updateByExampleSelective(any(), any())).thenReturn(3);

            assertThat(service.updateRecommendStatus(List.of(1L, 2L, 3L), 0)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("list - 分页查询品牌列表")
    class ListTests {
        @Test
        @DisplayName("无过滤条件时按排序逆序查询")
        void list_noFilters() {
            when(homeBrandMapper.selectByExample(any(SmsHomeBrandExample.class))).thenReturn(List.of());

            assertThat(service.list(null, null, 10, 1)).isEmpty();
        }
    }
}
