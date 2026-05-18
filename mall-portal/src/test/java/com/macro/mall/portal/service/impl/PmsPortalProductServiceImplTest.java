package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.PmsProductCategoryNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmsPortalProductServiceImpl 单元测试")
class PmsPortalProductServiceImplTest {

    private PmsProductMapper productMapper;
    private PmsProductCategoryMapper productCategoryMapper;
    private PmsBrandMapper brandMapper;
    private PmsProductAttributeMapper productAttributeMapper;
    private PmsProductAttributeValueMapper productAttributeValueMapper;
    private PmsSkuStockMapper skuStockMapper;
    private PmsProductLadderMapper productLadderMapper;
    private PmsProductFullReductionMapper productFullReductionMapper;
    private PortalProductDao portalProductDao;
    private PmsPortalProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productMapper = mock(PmsProductMapper.class);
        productCategoryMapper = mock(PmsProductCategoryMapper.class);
        brandMapper = mock(PmsBrandMapper.class);
        productAttributeMapper = mock(PmsProductAttributeMapper.class);
        productAttributeValueMapper = mock(PmsProductAttributeValueMapper.class);
        skuStockMapper = mock(PmsSkuStockMapper.class);
        productLadderMapper = mock(PmsProductLadderMapper.class);
        productFullReductionMapper = mock(PmsProductFullReductionMapper.class);
        portalProductDao = mock(PortalProductDao.class);
        service = new PmsPortalProductServiceImpl(productMapper, productCategoryMapper, brandMapper,
                productAttributeMapper, productAttributeValueMapper, skuStockMapper,
                productLadderMapper, productFullReductionMapper, portalProductDao);
    }

    @Nested
    @DisplayName("search 方法")
    class Search {

        @Test
        @DisplayName("按关键词搜索已发布商品")
        void search_withKeyword_returnsMatching() {
            PmsProduct product = new PmsProduct();
            product.setName("华为手机");
            when(productMapper.selectByExample(any())).thenReturn(List.of(product));

            List<PmsProduct> result = service.search("华为", null, null, 1, 10, 0);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("按新品排序时使用 id desc")
        void search_sortByNew_ordersByIdDesc() {
            when(productMapper.selectByExample(any())).thenReturn(List.of());

            service.search(null, null, null, 1, 10, 1);

            verify(productMapper).selectByExample(argThat(example -> {
                String orderBy = example.getOrderByClause();
                return orderBy != null && orderBy.contains("id desc");
            }));
        }
    }

    @Nested
    @DisplayName("categoryTreeList 方法")
    class CategoryTreeList {

        @Test
        @DisplayName("构建分类树：一级节点包含二级子节点")
        void categoryTreeList_buildsTree() {
            PmsProductCategory parent = new PmsProductCategory();
            parent.setId(1L);
            parent.setParentId(0L);

            PmsProductCategory child = new PmsProductCategory();
            child.setId(2L);
            child.setParentId(1L);

            when(productCategoryMapper.selectByExample(any())).thenReturn(List.of(parent, child));

            List<PmsProductCategoryNode> result = service.categoryTreeList();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChildren()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("detail 方法")
    class Detail {

        @Test
        @DisplayName("获取商品完整详情")
        void detail_returnsFullDetail() {
            PmsProduct product = new PmsProduct();
            product.setId(1L);
            product.setBrandId(10L);
            product.setProductAttributeCategoryId(100L);
            product.setPromotionType(0);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);
            when(brandMapper.selectByPrimaryKey(10L)).thenReturn(new PmsBrand());
            when(productAttributeMapper.selectByExample(any())).thenReturn(List.of());
            when(skuStockMapper.selectByExample(any())).thenReturn(List.of(new PmsSkuStock()));
            when(portalProductDao.getAvailableCouponList(1L, null)).thenReturn(List.of());

            PmsPortalProductDetail result = service.detail(1L);

            assertThat(result.getProduct()).isNotNull();
            assertThat(result.getBrand()).isNotNull();
            assertThat(result.getSkuStockList()).hasSize(1);
        }

        @Test
        @DisplayName("促销类型=3 时包含阶梯价格")
        void detail_promotionType3_includesLadder() {
            PmsProduct product = new PmsProduct();
            product.setId(1L);
            product.setBrandId(10L);
            product.setProductAttributeCategoryId(100L);
            product.setPromotionType(3);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);
            when(brandMapper.selectByPrimaryKey(10L)).thenReturn(new PmsBrand());
            when(productAttributeMapper.selectByExample(any())).thenReturn(List.of());
            when(skuStockMapper.selectByExample(any())).thenReturn(List.of());
            when(productLadderMapper.selectByExample(any())).thenReturn(List.of(new PmsProductLadder()));
            when(portalProductDao.getAvailableCouponList(1L, null)).thenReturn(List.of());

            PmsPortalProductDetail result = service.detail(1L);

            assertThat(result.getProductLadderList()).hasSize(1);
        }

        @Test
        @DisplayName("促销类型=4 时包含满减价格")
        void detail_promotionType4_includesFullReduction() {
            PmsProduct product = new PmsProduct();
            product.setId(1L);
            product.setBrandId(10L);
            product.setProductAttributeCategoryId(100L);
            product.setPromotionType(4);
            when(productMapper.selectByPrimaryKey(1L)).thenReturn(product);
            when(brandMapper.selectByPrimaryKey(10L)).thenReturn(new PmsBrand());
            when(productAttributeMapper.selectByExample(any())).thenReturn(List.of());
            when(skuStockMapper.selectByExample(any())).thenReturn(List.of());
            when(productFullReductionMapper.selectByExample(any())).thenReturn(List.of(new PmsProductFullReduction()));
            when(portalProductDao.getAvailableCouponList(1L, null)).thenReturn(List.of());

            PmsPortalProductDetail result = service.detail(1L);

            assertThat(result.getProductFullReductionList()).hasSize(1);
        }
    }
}
