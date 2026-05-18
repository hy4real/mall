package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberReadHistoryServiceImpl 单元测试")
class MemberReadHistoryServiceImplTest {

    private PmsProductMapper productMapper;
    private MemberReadHistoryRepository repository;
    private UmsMemberService memberService;
    private MemberReadHistoryServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        productMapper = mock(PmsProductMapper.class);
        repository = mock(MemberReadHistoryRepository.class);
        memberService = mock(UmsMemberService.class);
        service = new MemberReadHistoryServiceImpl(productMapper, repository, memberService);
        ReflectionTestUtils.setField(service, "sqlEnable", true);
        lenient().when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Nested
    @DisplayName("create 方法")
    class Create {

        @Test
        @DisplayName("productId 为 null 时返回 0")
        void create_nullProductId_returnsZero() {
            MemberReadHistory history = new MemberReadHistory();

            int result = service.create(history);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("sqlEnable=true 且商品已删除时返回 0")
        void create_deletedProduct_returnsZero() {
            MemberReadHistory history = new MemberReadHistory();
            history.setProductId(1L);
            PmsProduct product = new PmsProduct();
            product.setDeleteStatus(1);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);

            int result = service.create(history);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("保存浏览记录返回 1，清空 id 并设置创建时间")
        void create_valid_savesAndReturns1() {
            MemberReadHistory history = new MemberReadHistory();
            history.setProductId(1L);
            history.setId("old-id");
            PmsProduct product = new PmsProduct();
            product.setName("商品A");
            product.setPrice(new BigDecimal("99.00"));
            product.setDeleteStatus(0);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);

            int result = service.create(history);

            assertThat(result).isEqualTo(1);
            assertThat(history.getId()).isNull();
            assertThat(history.getCreateTime()).isNotNull();
            assertThat(history.getProductName()).isEqualTo("商品A");
            verify(repository).save(history);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class Delete {

        @Test
        @DisplayName("批量删除浏览记录")
        void delete_removesByIds() {
            List<String> ids = List.of("id1", "id2", "id3");

            int result = service.delete(ids);

            assertThat(result).isEqualTo(3);
            verify(repository).deleteAll(anyList());
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListHistory {

        @Test
        @DisplayName("分页查询浏览历史")
        void list_returnsPagedResult() {
            Page<MemberReadHistory> page = new PageImpl<>(java.util.List.of(new MemberReadHistory()));
            when(repository.findByMemberIdOrderByCreateTimeDesc(eq(1L), any(PageRequest.class))).thenReturn(page);

            Page<MemberReadHistory> result = service.list(1, 10);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("clear 方法")
    class Clear {

        @Test
        @DisplayName("清空所有浏览历史")
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
