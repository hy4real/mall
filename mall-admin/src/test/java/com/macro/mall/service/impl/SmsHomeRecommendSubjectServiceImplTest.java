package com.macro.mall.service.impl;

import com.macro.mall.mapper.SmsHomeRecommendSubjectMapper;
import com.macro.mall.model.SmsHomeRecommendSubject;
import com.macro.mall.model.SmsHomeRecommendSubjectExample;
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
@DisplayName("SmsHomeRecommendSubjectServiceImpl 单元测试")
class SmsHomeRecommendSubjectServiceImplTest {

    @Mock private SmsHomeRecommendSubjectMapper smsHomeRecommendSubjectMapper;
    private SmsHomeRecommendSubjectServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsHomeRecommendSubjectServiceImpl(smsHomeRecommendSubjectMapper);
    }

    @Nested
    @DisplayName("create - 批量添加首页推荐专题")
    class CreateTests {
        @Test
        @DisplayName("设置默认推荐状态和排序后逐条插入")
        void create_setsDefaults() {
            var s1 = new SmsHomeRecommendSubject();
            when(smsHomeRecommendSubjectMapper.insert(any())).thenReturn(1);

            int count = service.create(List.of(s1));

            assertThat(count).isEqualTo(1);
            assertThat(s1.getRecommendStatus()).isEqualTo(1);
            assertThat(s1.getSort()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("updateSort - 修改排序")
    class UpdateSortTests {
        @Test
        @DisplayName("按 ID 更新")
        void updateSort_delegates() {
            when(smsHomeRecommendSubjectMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            assertThat(service.updateSort(3L, 3)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete - 批量删除")
    class DeleteTests {
        @Test
        @DisplayName("按 ID 列表批量删除")
        void delete_batch() {
            when(smsHomeRecommendSubjectMapper.deleteByExample(any(SmsHomeRecommendSubjectExample.class))).thenReturn(2);

            assertThat(service.delete(List.of(1L, 2L))).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("updateRecommendStatus - 批量修改推荐状态")
    class UpdateRecommendStatusTests {
        @Test
        @DisplayName("批量更新")
        void updateRecommendStatus_batch() {
            when(smsHomeRecommendSubjectMapper.updateByExampleSelective(any(), any())).thenReturn(2);

            assertThat(service.updateRecommendStatus(List.of(1L, 2L), 1)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("list - 分页查询")
    class ListTests {
        @Test
        @DisplayName("无过滤条件时查询")
        void list_noFilters() {
            when(smsHomeRecommendSubjectMapper.selectByExample(any(SmsHomeRecommendSubjectExample.class))).thenReturn(List.of());

            assertThat(service.list(null, null, 10, 1)).isEmpty();
        }
    }
}
