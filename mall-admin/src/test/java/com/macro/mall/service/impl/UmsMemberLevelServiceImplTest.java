package com.macro.mall.service.impl;

import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.model.UmsMemberLevel;
import com.macro.mall.model.UmsMemberLevelExample;
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
@DisplayName("UmsMemberLevelServiceImpl 单元测试")
class UmsMemberLevelServiceImplTest {

    @Mock private UmsMemberLevelMapper memberLevelMapper;
    private UmsMemberLevelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UmsMemberLevelServiceImpl(memberLevelMapper);
    }

    @Nested
    @DisplayName("list - 根据默认状态查询会员等级")
    class ListTests {
        @Test
        @DisplayName("委托 mapper 按 defaultStatus 查询")
        void list_delegates() {
            var list = List.of(new UmsMemberLevel(), new UmsMemberLevel());
            when(memberLevelMapper.selectByExample(any(UmsMemberLevelExample.class))).thenReturn(list);

            assertThat(service.list(1)).hasSize(2);
        }
    }
}
