package com.macro.mall.service.impl;

import com.macro.mall.mapper.CmsSubjectMapper;
import com.macro.mall.model.CmsSubject;
import com.macro.mall.model.CmsSubjectExample;
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
@DisplayName("CmsSubjectServiceImpl 单元测试")
class CmsSubjectServiceImplTest {

    @Mock private CmsSubjectMapper subjectMapper;
    private CmsSubjectServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CmsSubjectServiceImpl(subjectMapper);
    }

    @Nested
    @DisplayName("listAll - 获取所有专题")
    class ListAllTests {
        @Test
        @DisplayName("查询全部专题")
        void listAll_delegates() {
            when(subjectMapper.selectByExample(any(CmsSubjectExample.class))).thenReturn(List.of(new CmsSubject()));

            assertThat(service.listAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("list - 分页查询专题")
    class ListTests {
        @Test
        @DisplayName("无关键词时分页查询")
        void list_noKeyword_returnsAll() {
            when(subjectMapper.selectByExample(any(CmsSubjectExample.class))).thenReturn(List.of());

            assertThat(service.list(null, 1, 10)).isEmpty();
        }
    }
}
