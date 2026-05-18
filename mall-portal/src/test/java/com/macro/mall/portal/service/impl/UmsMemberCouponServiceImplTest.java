package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsMemberCouponServiceImpl 单元测试")
class UmsMemberCouponServiceImplTest {

    private UmsMemberService memberService;
    private SmsCouponMapper couponMapper;
    private SmsCouponHistoryMapper couponHistoryMapper;
    private SmsCouponHistoryDao couponHistoryDao;
    private SmsCouponProductRelationMapper couponProductRelationMapper;
    private SmsCouponProductCategoryRelationMapper couponProductCategoryRelationMapper;
    private PmsProductMapper productMapper;
    private UmsMemberCouponServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        memberService = mock(UmsMemberService.class);
        couponMapper = mock(SmsCouponMapper.class);
        couponHistoryMapper = mock(SmsCouponHistoryMapper.class);
        couponHistoryDao = mock(SmsCouponHistoryDao.class);
        couponProductRelationMapper = mock(SmsCouponProductRelationMapper.class);
        couponProductCategoryRelationMapper = mock(SmsCouponProductCategoryRelationMapper.class);
        productMapper = mock(PmsProductMapper.class);
        service = new UmsMemberCouponServiceImpl(memberService, couponMapper, couponHistoryMapper,
                couponHistoryDao, couponProductRelationMapper, couponProductCategoryRelationMapper, productMapper);
        lenient().when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Nested
    @DisplayName("add 方法")
    class Add {

        @Test
        @DisplayName("优惠券不存在时抛异常")
        void add_couponNotFound_throws() {
            when(couponMapper.selectByPrimaryKey(1L)).thenReturn(null);

            assertThatThrownBy(() -> service.add(1L))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("优惠券已领完时抛异常")
        void add_couponExhausted_throws() {
            SmsCoupon coupon = new SmsCoupon();
            coupon.setId(1L);
            coupon.setCount(0);
            coupon.setEnableTime(new Date(System.currentTimeMillis() - 86400000));
            when(couponMapper.selectByPrimaryKey(1L)).thenReturn(coupon);

            assertThatThrownBy(() -> service.add(1L))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("超过领取限制时抛异常")
        void add_exceedsLimit_throws() {
            SmsCoupon coupon = new SmsCoupon();
            coupon.setId(1L);
            coupon.setCount(10);
            coupon.setEnableTime(new Date(System.currentTimeMillis() - 86400000));
            coupon.setPerLimit(1);
            when(couponMapper.selectByPrimaryKey(1L)).thenReturn(coupon);
            when(couponHistoryMapper.countByExample(any())).thenReturn(1L);

            assertThatThrownBy(() -> service.add(1L))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("正常领取优惠券")
        void add_valid_claimsCoupon() {
            SmsCoupon coupon = new SmsCoupon();
            coupon.setId(1L);
            coupon.setCount(10);
            coupon.setEnableTime(new Date(System.currentTimeMillis() - 86400000));
            coupon.setPerLimit(5);
            coupon.setReceiveCount(0);
            when(couponMapper.selectByPrimaryKey(1L)).thenReturn(coupon);
            when(couponHistoryMapper.countByExample(any())).thenReturn(0L);
            when(couponHistoryMapper.insert(any())).thenReturn(1);
            when(couponMapper.updateByPrimaryKey(any())).thenReturn(1);

            service.add(1L);

            verify(couponHistoryMapper).insert(any());
            verify(couponMapper).updateByPrimaryKey(any());
            assertThat(coupon.getCount()).isEqualTo(9);
            assertThat(coupon.getReceiveCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("listHistory 方法")
    class ListHistory {

        @Test
        @DisplayName("查询用户优惠券历史")
        void listHistory_returnsHistory() {
            when(couponHistoryMapper.selectByExample(any())).thenReturn(List.of(new SmsCouponHistory()));

            List<SmsCouponHistory> result = service.listHistory(null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("按使用状态过滤")
        void listHistory_withStatus_filters() {
            when(couponHistoryMapper.selectByExample(any())).thenReturn(List.of());

            List<SmsCouponHistory> result = service.listHistory(0);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listCart 方法")
    class ListCart {

        @Test
        @DisplayName("购物车为空时返回空列表")
        void listCart_emptyCart_returnsEmpty() {
            when(couponHistoryDao.getDetailList(anyLong())).thenReturn(List.of());

            List<SmsCouponHistoryDetail> result = service.listCart(List.of(), 1);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("全场通用券不满足金额条件时在不可用列表")
        void listCart_generalCoupon_belowMinPoint_inDisableList() {
            SmsCouponHistoryDetail detail = buildDetail(0, new BigDecimal("200.00"), futureDate());
            when(couponHistoryDao.getDetailList(anyLong())).thenReturn(List.of(detail));

            CartPromotionItem item = new CartPromotionItem();
            item.setPrice(new BigDecimal("100.00"));
            item.setReduceAmount(BigDecimal.ZERO);
            item.setQuantity(1);

            List<SmsCouponHistoryDetail> result = service.listCart(List.of(item), 0);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("listByProduct 方法")
    class ListByProduct {

        @Test
        @DisplayName("根据商品 ID 查询可用优惠券")
        void listByProduct_returnsCoupons() {
            when(couponProductRelationMapper.selectByExample(any())).thenReturn(List.of());
            PmsProduct product = new PmsProduct();
            product.setProductCategoryId(1L);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);
            when(couponProductCategoryRelationMapper.selectByExample(any())).thenReturn(List.of());
            when(couponMapper.selectByExample(any())).thenReturn(List.of(new SmsCoupon()));

            List<SmsCoupon> result = service.listByProduct(1L);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListCoupons {

        @Test
        @DisplayName("按使用状态查询会员优惠券")
        void list_returnsMemberCoupons() {
            when(couponHistoryDao.getCouponList(1L, 0)).thenReturn(java.util.List.of(new SmsCoupon()));

            java.util.List<SmsCoupon> result = service.list(0);

            assertThat(result).hasSize(1);
        }
    }

    private UmsMember createMember(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        m.setNickname("testUser");
        return m;
    }

    private SmsCouponHistoryDetail buildDetail(int useType, BigDecimal minPoint, Date endTime) {
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        SmsCoupon coupon = new SmsCoupon();
        coupon.setUseType(useType);
        coupon.setMinPoint(minPoint);
        coupon.setEndTime(endTime);
        coupon.setStartTime(new Date(System.currentTimeMillis() - 86400000));
        detail.setCoupon(coupon);
        detail.setCategoryRelationList(List.of());
        detail.setProductRelationList(List.of());
        return detail;
    }

    private Date futureDate() {
        return new Date(System.currentTimeMillis() + 86400000 * 30);
    }
}
