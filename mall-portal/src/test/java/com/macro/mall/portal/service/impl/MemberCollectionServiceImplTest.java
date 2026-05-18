package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
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
@DisplayName("MemberCollectionServiceImpl 单元测试")
class MemberCollectionServiceImplTest {

    private PmsProductMapper productMapper;
    private MemberProductCollectionRepository repository;
    private UmsMemberService memberService;
    private MemberCollectionServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        productMapper = mock(PmsProductMapper.class);
        repository = mock(MemberProductCollectionRepository.class);
        memberService = mock(UmsMemberService.class);
        service = new MemberCollectionServiceImpl(productMapper, repository, memberService);
        ReflectionTestUtils.setField(service, "sqlEnable", true);
        lenient().when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Nested
    @DisplayName("add 方法")
    class Add {

        @Test
        @DisplayName("productId 为 null 时返回 0")
        void add_nullProductId_returnsZero() {
            MemberProductCollection collection = new MemberProductCollection();

            int result = service.add(collection);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("已收藏时返回 0")
        void add_alreadyExists_returnsZero() {
            MemberProductCollection collection = new MemberProductCollection();
            collection.setProductId(1L);
            when(repository.findByMemberIdAndProductId(1L, 1L)).thenReturn(new MemberProductCollection());

            int result = service.add(collection);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("商品已删除时返回 0")
        void add_deletedProduct_returnsZero() {
            MemberProductCollection collection = new MemberProductCollection();
            collection.setProductId(1L);
            when(repository.findByMemberIdAndProductId(1L, 1L)).thenReturn(null);
            PmsProduct product = new PmsProduct();
            product.setDeleteStatus(1);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);

            int result = service.add(collection);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("新收藏保存成功返回 1")
        void add_new_savesAndReturns1() {
            MemberProductCollection collection = new MemberProductCollection();
            collection.setProductId(1L);
            when(repository.findByMemberIdAndProductId(1L, 1L)).thenReturn(null);
            PmsProduct product = new PmsProduct();
            product.setName("商品A");
            product.setDeleteStatus(0);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);

            int result = service.add(collection);

            assertThat(result).isEqualTo(1);
            verify(repository).save(collection);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class Delete {

        @Test
        @DisplayName("取消收藏")
        void delete_callsRepository() {
            when(repository.deleteByMemberIdAndProductId(1L, 10L)).thenReturn(1);

            int result = service.delete(10L);

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListCollection {

        @Test
        @DisplayName("分页查询收藏列表")
        void list_returnsPagedResult() {
            Page<MemberProductCollection> page = new PageImpl<>(java.util.List.of(new MemberProductCollection()));
            when(repository.findByMemberId(eq(1L), any(PageRequest.class))).thenReturn(page);

            Page<MemberProductCollection> result = service.list(1, 10);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("clear 方法")
    class Clear {

        @Test
        @DisplayName("清空所有收藏")
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
