package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberBrandAttention;
import com.macro.mall.portal.repository.MemberBrandAttentionRepository;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberAttentionServiceImpl 单元测试")
class MemberAttentionServiceImplTest {

    private PmsBrandMapper brandMapper;
    private MemberBrandAttentionRepository repository;
    private UmsMemberService memberService;
    private MemberAttentionServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        brandMapper = mock(PmsBrandMapper.class);
        repository = mock(MemberBrandAttentionRepository.class);
        memberService = mock(UmsMemberService.class);
        service = new MemberAttentionServiceImpl(brandMapper, repository, memberService);
        ReflectionTestUtils.setField(service, "sqlEnable", true);
        lenient().when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Nested
    @DisplayName("add 方法")
    class Add {

        @Test
        @DisplayName("brandId 为 null 时返回 0")
        void add_nullBrandId_returnsZero() {
            MemberBrandAttention attention = new MemberBrandAttention();

            int result = service.add(attention);

            assertThat(result).isEqualTo(0);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("已关注时返回 0 不重复保存")
        void add_alreadyExists_returnsZero() {
            MemberBrandAttention attention = new MemberBrandAttention();
            attention.setBrandId(1L);
            when(repository.findByMemberIdAndBrandId(1L, 1L)).thenReturn(new MemberBrandAttention());

            int result = service.add(attention);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("sqlEnable=true 且品牌不存在时返回 0")
        void add_sqlEnable_brandNotExists_returnsZero() {
            MemberBrandAttention attention = new MemberBrandAttention();
            attention.setBrandId(99L);
            when(repository.findByMemberIdAndBrandId(1L, 99L)).thenReturn(null);
            when(brandMapper.selectByPrimaryKey(99L)).thenReturn(null);

            int result = service.add(attention);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("新关注保存成功返回 1")
        void add_new_savesAndReturns1() {
            MemberBrandAttention attention = new MemberBrandAttention();
            attention.setBrandId(1L);
            PmsBrand brand = new PmsBrand();
            brand.setName("华为");
            brand.setLogo("logo.png");
            when(repository.findByMemberIdAndBrandId(1L, 1L)).thenReturn(null);
            when(brandMapper.selectByPrimaryKey(1L)).thenReturn(brand);

            int result = service.add(attention);

            assertThat(result).isEqualTo(1);
            verify(repository).save(attention);
            assertThat(attention.getBrandName()).isEqualTo("华为");
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class Delete {

        @Test
        @DisplayName("删除关注")
        void delete_callsRepository() {
            when(repository.deleteByMemberIdAndBrandId(1L, 10L)).thenReturn(1);

            int result = service.delete(10L);

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListAttention {

        @Test
        @DisplayName("分页查询关注列表")
        void list_returnsPagedResult() {
            Page<MemberBrandAttention> page = new PageImpl<>(java.util.List.of(new MemberBrandAttention()));
            when(repository.findByMemberId(eq(1L), any(PageRequest.class))).thenReturn(page);

            Page<MemberBrandAttention> result = service.list(1, 10);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("detail 方法")
    class Detail {

        @Test
        @DisplayName("查询关注详情")
        void detail_returnsAttention() {
            MemberBrandAttention attention = new MemberBrandAttention();
            when(repository.findByMemberIdAndBrandId(1L, 10L)).thenReturn(attention);

            MemberBrandAttention result = service.detail(10L);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("clear 方法")
    class Clear {

        @Test
        @DisplayName("清空所有关注")
        void clear_deletesAll() {
            service.clear();
            verify(repository).deleteAllByMemberId(1L);
        }
    }

    private UmsMember createMember(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        m.setNickname("testUser");
        m.setIcon("icon.png");
        return m;
    }
}
