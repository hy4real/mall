package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsCartItemServiceImpl 单元测试")
class OmsCartItemServiceImplTest {

    private OmsCartItemMapper cartItemMapper;
    private PortalProductDao productDao;
    private OmsPromotionService promotionService;
    private UmsMemberService memberService;
    private OmsCartItemServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        cartItemMapper = mock(OmsCartItemMapper.class);
        productDao = mock(PortalProductDao.class);
        promotionService = mock(OmsPromotionService.class);
        memberService = mock(UmsMemberService.class);
        service = new OmsCartItemServiceImpl(cartItemMapper, productDao, promotionService, memberService);
        lenient().when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Nested
    @DisplayName("add 方法")
    class Add {

        @Test
        @DisplayName("购物车不存在该商品时新增")
        void add_newItem_insertsNew() {
            OmsCartItem cartItem = new OmsCartItem();
            cartItem.setProductId(1L);
            cartItem.setProductSkuId(10L);
            cartItem.setQuantity(2);
            when(cartItemMapper.selectByExample(any())).thenReturn(List.of());
            when(cartItemMapper.insert(any())).thenReturn(1);

            int result = service.add(cartItem);

            assertThat(result).isEqualTo(1);
            verify(cartItemMapper).insert(any());
        }

        @Test
        @DisplayName("购物车已有该商品时累加数量")
        void add_existingItem_updatesQuantity() {
            OmsCartItem cartItem = new OmsCartItem();
            cartItem.setProductId(1L);
            cartItem.setProductSkuId(10L);
            cartItem.setQuantity(3);

            OmsCartItem existing = new OmsCartItem();
            existing.setId(100L);
            existing.setQuantity(2);
            when(cartItemMapper.selectByExample(any())).thenReturn(List.of(existing));
            when(cartItemMapper.updateByPrimaryKey(any())).thenReturn(1);

            int result = service.add(cartItem);

            assertThat(result).isEqualTo(1);
            assertThat(existing.getQuantity()).isEqualTo(5);
            verify(cartItemMapper).updateByPrimaryKey(existing);
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListCartItems {

        @Test
        @DisplayName("查询用户未删除的购物车商品")
        void list_returnsActiveItems() {
            OmsCartItem item = new OmsCartItem();
            when(cartItemMapper.selectByExample(any())).thenReturn(List.of(item));

            java.util.List<OmsCartItem> result = service.list(1L);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("listPromotion 方法")
    class ListPromotion {

        @Test
        @DisplayName("指定 cartIds 时只计算选中商品")
        void listPromotion_withCartIds_filtersItems() {
            OmsCartItem item1 = new OmsCartItem();
            item1.setId(1L);
            item1.setProductId(10L);
            OmsCartItem item2 = new OmsCartItem();
            item2.setId(2L);
            item2.setProductId(20L);
            when(cartItemMapper.selectByExample(any())).thenReturn(java.util.List.of(item1, item2));
            when(promotionService.calcCartPromotion(any())).thenReturn(java.util.List.of(new CartPromotionItem()));

            java.util.List<CartPromotionItem> result = service.listPromotion(1L, java.util.List.of(1L));

            assertThat(result).hasSize(1);
            verify(promotionService).calcCartPromotion(argThat(list -> list.size() == 1));
        }

        @Test
        @DisplayName("购物车为空时返回空列表")
        void listPromotion_emptyCart_returnsEmpty() {
            when(cartItemMapper.selectByExample(any())).thenReturn(java.util.List.of());

            java.util.List<CartPromotionItem> result = service.listPromotion(1L, null);

            assertThat(result).isEmpty();
            verify(promotionService, never()).calcCartPromotion(any());
        }
    }

    @Nested
    @DisplayName("updateQuantity 方法")
    class UpdateQuantity {

        @Test
        @DisplayName("更新购物车商品数量")
        void updateQuantity_updatesItem() {
            when(cartItemMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            int result = service.updateQuantity(1L, 1L, 5);

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class Delete {

        @Test
        @DisplayName("软删除购物车商品")
        void delete_softDeletes() {
            when(cartItemMapper.updateByExampleSelective(any(), any())).thenReturn(2);

            int result = service.delete(1L, java.util.List.of(1L, 2L));

            assertThat(result).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getCartProduct 方法")
    class GetCartProduct {

        @Test
        @DisplayName("获取商品信息（含属性和 SKU）")
        void getCartProduct_returnsProduct() {
            CartProduct product = new CartProduct();
            when(productDao.getCartProduct(1L)).thenReturn(product);

            CartProduct result = service.getCartProduct(1L);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("clear 方法")
    class Clear {

        @Test
        @DisplayName("清空用户购物车（软删除）")
        void clear_softDeletesAll() {
            when(cartItemMapper.updateByExampleSelective(any(), any())).thenReturn(5);

            int result = service.clear(1L);

            assertThat(result).isEqualTo(5);
        }
    }

    private UmsMember createMember(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        m.setNickname("testUser");
        return m;
    }
}
