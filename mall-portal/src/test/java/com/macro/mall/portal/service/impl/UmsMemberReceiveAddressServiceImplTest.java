package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsMemberReceiveAddressServiceImpl 单元测试")
class UmsMemberReceiveAddressServiceImplTest {

    private UmsMemberService memberService;
    private UmsMemberReceiveAddressMapper addressMapper;
    private UmsMemberReceiveAddressServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        memberService = mock(UmsMemberService.class);
        addressMapper = mock(UmsMemberReceiveAddressMapper.class);
        service = new UmsMemberReceiveAddressServiceImpl(memberService, addressMapper);
        when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Nested
    @DisplayName("add 方法")
    class Add {

        @Test
        @DisplayName("新增地址时设置 memberId")
        void add_setsMemberId() {
            UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
            address.setName("张三");
            when(addressMapper.insert(any())).thenReturn(1);

            int result = service.add(address);

            assertThat(result).isEqualTo(1);
            assertThat(address.getMemberId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class Delete {

        @Test
        @DisplayName("删除地址限定当前用户")
        void delete_scopedToMember() {
            when(addressMapper.deleteByExample(any())).thenReturn(1);

            int result = service.delete(10L);

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("update 方法")
    class Update {

        @Test
        @DisplayName("设置默认地址时先清除旧默认地址")
        void update_setDefault_clearsOldDefault() {
            UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
            address.setDefaultStatus(1);
            when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            service.update(10L, address);

            // 清除旧默认 + 设置新默认 = 2 次 updateByExampleSelective
            verify(addressMapper, times(2)).updateByExampleSelective(any(), any());
        }

        @Test
        @DisplayName("不设默认地址时 defaultStatus 为 0")
        void update_noDefault_setsZero() {
            UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
            address.setDefaultStatus(null);
            when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            service.update(10L, address);

            assertThat(address.getDefaultStatus()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListAddress {

        @Test
        @DisplayName("返回当前用户的所有地址")
        void list_returnsMemberAddresses() {
            UmsMemberReceiveAddress addr = new UmsMemberReceiveAddress();
            when(addressMapper.selectByExample(any())).thenReturn(java.util.List.of(addr));

            java.util.List<UmsMemberReceiveAddress> result = service.list();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getItem 方法")
    class GetItem {

        @Test
        @DisplayName("能查到时返回地址")
        void getItem_found_returnsAddress() {
            UmsMemberReceiveAddress addr = new UmsMemberReceiveAddress();
            when(addressMapper.selectByExample(any())).thenReturn(List.of(addr));

            UmsMemberReceiveAddress result = service.getItem(10L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("查不到时返回 null")
        void getItem_notFound_returnsNull() {
            when(addressMapper.selectByExample(any())).thenReturn(Collections.emptyList());

            UmsMemberReceiveAddress result = service.getItem(99L);

            assertThat(result).isNull();
        }
    }

    private UmsMember createMember(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        m.setNickname("testUser");
        return m;
    }
}
