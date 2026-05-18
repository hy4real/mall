package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.service.HomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeServiceImpl 单元测试")
class HomeServiceImplTest {

    private SmsHomeAdvertiseMapper advertiseMapper;
    private HomeDao homeDao;
    private SmsFlashPromotionMapper flashPromotionMapper;
    private SmsFlashPromotionSessionMapper promotionSessionMapper;
    private PmsProductMapper productMapper;
    private PmsProductCategoryMapper productCategoryMapper;
    private CmsSubjectMapper subjectMapper;
    private HomeServiceImpl service;

    @BeforeEach
    void setUp() {
        advertiseMapper = mock(SmsHomeAdvertiseMapper.class);
        homeDao = mock(HomeDao.class);
        flashPromotionMapper = mock(SmsFlashPromotionMapper.class);
        promotionSessionMapper = mock(SmsFlashPromotionSessionMapper.class);
        productMapper = mock(PmsProductMapper.class);
        productCategoryMapper = mock(PmsProductCategoryMapper.class);
        subjectMapper = mock(CmsSubjectMapper.class);
        service = new HomeServiceImpl(advertiseMapper, homeDao, flashPromotionMapper,
                promotionSessionMapper, productMapper, productCategoryMapper, subjectMapper);
    }

    @Nested
    @DisplayName("content 方法")
    class Content {

        @Test
        @DisplayName("聚合首页所有内容")
        void content_returnsAggregatedResult() {
            when(advertiseMapper.selectByExample(any())).thenReturn(List.of());
            when(homeDao.getRecommendBrandList(0, 6)).thenReturn(List.of());
            when(flashPromotionMapper.selectByExample(any())).thenReturn(List.of());
            when(homeDao.getNewProductList(0, 4)).thenReturn(List.of());
            when(homeDao.getHotProductList(0, 4)).thenReturn(List.of());
            when(homeDao.getRecommendSubjectList(0, 4)).thenReturn(List.of());

            HomeContentResult result = service.content();

            assertThat(result).isNotNull();
            verify(homeDao).getRecommendBrandList(0, 6);
            verify(homeDao).getNewProductList(0, 4);
            verify(homeDao).getHotProductList(0, 4);
            verify(homeDao).getRecommendSubjectList(0, 4);
        }

        @Test
        @DisplayName("有秒杀活动时返回秒杀信息")
        void content_withFlashPromotion_returnsFlashInfo() {
            SmsFlashPromotion flashPromotion = new SmsFlashPromotion();
            flashPromotion.setId(1L);
            flashPromotion.setStatus(1);
            flashPromotion.setStartDate(new Date());
            flashPromotion.setEndDate(new Date());

            SmsFlashPromotionSession session = new SmsFlashPromotionSession();
            session.setId(1L);
            session.setStartTime(new Date());
            session.setEndTime(new Date());

            when(advertiseMapper.selectByExample(any())).thenReturn(List.of());
            when(homeDao.getRecommendBrandList(0, 6)).thenReturn(List.of());
            when(flashPromotionMapper.selectByExample(any())).thenReturn(List.of(flashPromotion));
            when(promotionSessionMapper.selectByExample(any())).thenReturn(List.of(session));
            when(homeDao.getFlashProductList(1L, 1L)).thenReturn(List.of());
            when(homeDao.getNewProductList(0, 4)).thenReturn(List.of());
            when(homeDao.getHotProductList(0, 4)).thenReturn(List.of());
            when(homeDao.getRecommendSubjectList(0, 4)).thenReturn(List.of());

            HomeContentResult result = service.content();

            assertThat(result.getHomeFlashPromotion()).isNotNull();
            assertThat(result.getHomeFlashPromotion().getProductList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("recommendProductList 方法")
    class RecommendProductList {

        @Test
        @DisplayName("查询已发布商品")
        void recommendProductList_returnsPublishedProducts() {
            when(productMapper.selectByExample(any())).thenReturn(List.of(new PmsProduct()));

            List<PmsProduct> result = service.recommendProductList(10, 1);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getProductCateList 方法")
    class GetProductCateList {

        @Test
        @DisplayName("按 parentId 查询显示的分类")
        void getProductCateList_returnsCategories() {
            when(productCategoryMapper.selectByExample(any())).thenReturn(List.of(new PmsProductCategory()));

            List<PmsProductCategory> result = service.getProductCateList(0L);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("hotProductList / newProductList 方法")
    class ProductLists {

        @Test
        @DisplayName("热门商品列表使用 offset 分页")
        void hotProductList_usesOffset() {
            when(homeDao.getHotProductList(10, 5)).thenReturn(List.of(new PmsProduct()));

            List<PmsProduct> result = service.hotProductList(3, 5);

            assertThat(result).hasSize(1);
            verify(homeDao).getHotProductList(10, 5);
        }

        @Test
        @DisplayName("新品列表使用 offset 分页")
        void newProductList_usesOffset() {
            when(homeDao.getNewProductList(0, 4)).thenReturn(List.of());

            List<PmsProduct> result = service.newProductList(1, 4);

            assertThat(result).isEmpty();
        }
    }
}
