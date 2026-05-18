package com.macro.mall.service.impl;

import com.macro.mall.mapper.CmsPrefrenceAreaMapper;
import com.macro.mall.model.CmsPrefrenceArea;
import com.macro.mall.model.CmsPrefrenceAreaExample;
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
@DisplayName("CmsPrefrenceAreaServiceImpl 单元测试")
class CmsPrefrenceAreaServiceImplTest {

    @Mock private CmsPrefrenceAreaMapper prefrenceAreaMapper;
    private CmsPrefrenceAreaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CmsPrefrenceAreaServiceImpl(prefrenceAreaMapper);
    }

    @Nested
    @DisplayName("listAll - 获取所有优选专区")
    class ListAllTests {
        @Test
        @DisplayName("委托 mapper 查询全部")
        void listAll_delegates() {
            var list = List.of(new CmsPrefrenceArea(), new CmsPrefrenceArea());
            when(prefrenceAreaMapper.selectByExample(any(CmsPrefrenceAreaExample.class))).thenReturn(list);

            assertThat(service.listAll()).hasSize(2);
        }
    }
}
