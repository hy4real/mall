package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
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
@DisplayName("PmsPortalBrandServiceImpl 单元测试")
class PmsPortalBrandServiceImplTest {

    private HomeDao homeDao;
    private PmsBrandMapper brandMapper;
    private PmsProductMapper productMapper;
    private PmsPortalBrandServiceImpl service;

    @BeforeEach
    void setUp() {
        homeDao = mock(HomeDao.class);
        brandMapper = mock(PmsBrandMapper.class);
        productMapper = mock(PmsProductMapper.class);
        service = new PmsPortalBrandServiceImpl(homeDao, brandMapper, productMapper);
    }

    @Nested
    @DisplayName("recommendList 方法")
    class RecommendList {

        @Test
        @DisplayName("正确计算 offset 并返回推荐品牌列表")
        void recommendList_page2_returnsCorrectOffset() {
            PmsBrand brand = new PmsBrand();
            when(homeDao.getRecommendBrandList(10, 5)).thenReturn(List.of(brand));

            List<PmsBrand> result = service.recommendList(3, 5);

            assertThat(result).hasSize(1);
            verify(homeDao).getRecommendBrandList(10, 5);
        }
    }

    @Nested
    @DisplayName("detail 方法")
    class Detail {

        @Test
        @DisplayName("根据 ID 查询品牌详情")
        void detail_existingId_returnsBrand() {
            PmsBrand brand = new PmsBrand();
            brand.setId(1L);
            brand.setName("华为");
            when(brandMapper.selectByPrimaryKey(1L)).thenReturn(brand);

            PmsBrand result = service.detail(1L);

            assertThat(result.getName()).isEqualTo("华为");
        }
    }

    @Nested
    @DisplayName("productList 方法")
    class ProductList {

        @Test
        @DisplayName("分页查询品牌下的已发布商品")
        void productList_returnsCommonPage() {
            PmsProduct product = new PmsProduct();
            product.setBrandId(1L);
            when(productMapper.selectByExample(any())).thenReturn(List.of(product));

            CommonPage<PmsProduct> result = service.productList(1L, 1, 10);

            assertThat(result.getList()).hasSize(1);
        }
    }
}
