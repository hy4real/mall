package com.macro.mall.service.impl;

import com.macro.mall.mapper.OmsCompanyAddressMapper;
import com.macro.mall.model.OmsCompanyAddress;
import com.macro.mall.model.OmsCompanyAddressExample;
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
@DisplayName("OmsCompanyAddressServiceImpl 单元测试")
class OmsCompanyAddressServiceImplTest {

    @Mock private OmsCompanyAddressMapper companyAddressMapper;
    private OmsCompanyAddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OmsCompanyAddressServiceImpl(companyAddressMapper);
    }

    @Nested
    @DisplayName("list - 获取所有公司地址")
    class ListTests {
        @Test
        @DisplayName("委托 mapper 查询全部")
        void list_delegates() {
            var list = List.of(new OmsCompanyAddress());
            when(companyAddressMapper.selectByExample(any(OmsCompanyAddressExample.class))).thenReturn(list);

            assertThat(service.list()).hasSize(1);
        }
    }
}
