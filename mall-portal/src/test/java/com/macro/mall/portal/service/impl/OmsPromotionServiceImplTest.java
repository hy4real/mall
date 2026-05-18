package com.macro.mall.portal.service.impl;

import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.PromotionProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPromotionServiceImpl 单元测试")
class OmsPromotionServiceImplTest {

    private PortalProductDao portalProductDao;
    private OmsPromotionServiceImpl service;

    @BeforeEach
    void setUp() {
        portalProductDao = mock(PortalProductDao.class);
        service = new OmsPromotionServiceImpl(portalProductDao);
    }

    @Nested
    @DisplayName("calcCartPromotion 方法")
    class CalcCartPromotion {

        private PromotionProduct buildPromotionProduct(Long productId, int promotionType) {
            PromotionProduct pp = new PromotionProduct();
            pp.setId(productId);
            pp.setPromotionType(promotionType);
            pp.setGiftPoint(10);
            pp.setGiftGrowth(20);

            PmsSkuStock sku = new PmsSkuStock();
            sku.setId(100L);
            sku.setPrice(new BigDecimal("100.00"));
            sku.setPromotionPrice(new BigDecimal("80.00"));
            sku.setStock(50);
            sku.setLockStock(5);
            pp.setSkuStockList(List.of(sku));
            return pp;
        }

        private OmsCartItem buildCartItem(Long productId, Long skuId, int quantity) {
            OmsCartItem item = new OmsCartItem();
            item.setProductId(productId);
            item.setProductSkuId(skuId);
            item.setQuantity(quantity);
            return item;
        }

        @Test
        @DisplayName("单品促销：原价 - 促销价作为减价金额")
        void calcCartPromotion_singlePromotion_calculatesReduce() {
            PromotionProduct pp = buildPromotionProduct(1L, 1);
            OmsCartItem item = buildCartItem(1L, 100L, 1);
            when(portalProductDao.getPromotionProductList(List.of(1L))).thenReturn(List.of(pp));

            List<CartPromotionItem> result = service.calcCartPromotion(List.of(item));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPromotionMessage()).isEqualTo("单品促销");
            assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("20.00");
            assertThat(result.get(0).getPrice()).isEqualByComparingTo("100.00");
            assertThat(result.get(0).getRealStock()).isEqualTo(45);
        }

        @Test
        @DisplayName("打折优惠：满足阶梯数量时打折")
        void calcCartPromotion_ladderPromotion_appliesDiscount() {
            PromotionProduct pp = buildPromotionProduct(1L, 3);
            PmsProductLadder ladder = new PmsProductLadder();
            ladder.setCount(2);
            ladder.setDiscount(new BigDecimal("0.8"));
            pp.setProductLadderList(new ArrayList<>(List.of(ladder)));

            OmsCartItem item1 = buildCartItem(1L, 100L, 1);
            OmsCartItem item2 = buildCartItem(1L, 100L, 1);
            when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(pp));

            List<CartPromotionItem> result = service.calcCartPromotion(List.of(item1, item2));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPromotionMessage()).contains("打折优惠");
            assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("满减优惠：满足满减金额时减价")
        void calcCartPromotion_fullReduction_appliesReduction() {
            PromotionProduct pp = buildPromotionProduct(1L, 4);
            PmsProductFullReduction reduction = new PmsProductFullReduction();
            reduction.setFullPrice(new BigDecimal("100.00"));
            reduction.setReducePrice(new BigDecimal("10.00"));
            pp.setProductFullReductionList(new ArrayList<>(List.of(reduction)));

            OmsCartItem item = buildCartItem(1L, 100L, 2);
            when(portalProductDao.getPromotionProductList(List.of(1L))).thenReturn(List.of(pp));

            List<CartPromotionItem> result = service.calcCartPromotion(List.of(item));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPromotionMessage()).contains("满减优惠");
        }

        @Test
        @DisplayName("无促销类型时显示无优惠")
        void calcCartPromotion_noPromotion_showsNoPromotion() {
            PromotionProduct pp = buildPromotionProduct(1L, 0);
            OmsCartItem item = buildCartItem(1L, 100L, 1);
            when(portalProductDao.getPromotionProductList(List.of(1L))).thenReturn(List.of(pp));

            List<CartPromotionItem> result = service.calcCartPromotion(List.of(item));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPromotionMessage()).isEqualTo("无优惠");
            assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("不同商品的购物车项按 SPU 分组计算")
        void calcCartPromotion_differentProducts_groupedBySpu() {
            PromotionProduct pp1 = buildPromotionProduct(1L, 0);
            PromotionProduct pp2 = buildPromotionProduct(2L, 0);

            PmsSkuStock sku2 = new PmsSkuStock();
            sku2.setId(200L);
            sku2.setPrice(new BigDecimal("200.00"));
            sku2.setStock(30);
            sku2.setLockStock(0);
            pp2.setSkuStockList(List.of(sku2));

            OmsCartItem item1 = buildCartItem(1L, 100L, 1);
            OmsCartItem item2 = buildCartItem(2L, 200L, 2);
            when(portalProductDao.getPromotionProductList(List.of(1L, 2L))).thenReturn(List.of(pp1, pp2));

            List<CartPromotionItem> result = service.calcCartPromotion(List.of(item1, item2));

            assertThat(result).hasSize(2);
        }
    }
}
