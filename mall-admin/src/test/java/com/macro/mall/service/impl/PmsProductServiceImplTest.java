package com.macro.mall.service.impl;

import com.macro.mall.dao.*;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.dto.PmsProductResult;
import com.macro.mall.mapper.*;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.model.PmsProductVertifyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmsProductServiceImpl 单元测试")
class PmsProductServiceImplTest {

    @Mock private PmsProductMapper productMapper;
    @Mock private PmsMemberPriceDao memberPriceDao;
    @Mock private PmsMemberPriceMapper memberPriceMapper;
    @Mock private PmsProductLadderDao productLadderDao;
    @Mock private PmsProductLadderMapper productLadderMapper;
    @Mock private PmsProductFullReductionDao productFullReductionDao;
    @Mock private PmsProductFullReductionMapper productFullReductionMapper;
    @Mock private PmsSkuStockDao skuStockDao;
    @Mock private PmsSkuStockMapper skuStockMapper;
    @Mock private PmsProductAttributeValueDao productAttributeValueDao;
    @Mock private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Mock private CmsSubjectProductRelationDao subjectProductRelationDao;
    @Mock private CmsSubjectProductRelationMapper subjectProductRelationMapper;
    @Mock private CmsPrefrenceAreaProductRelationDao prefrenceAreaProductRelationDao;
    @Mock private CmsPrefrenceAreaProductRelationMapper prefrenceAreaProductRelationMapper;
    @Mock private PmsProductDao productDao;
    @Mock private PmsProductVertifyRecordDao productVertifyRecordDao;

    @Captor private ArgumentCaptor<PmsProduct> productCaptor;
    @Captor private ArgumentCaptor<List<PmsProductVertifyRecord>> vertifyRecordListCaptor;

    private PmsProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new PmsProductServiceImpl(
                productMapper, memberPriceDao, memberPriceMapper,
                productLadderDao, productLadderMapper,
                productFullReductionDao, productFullReductionMapper,
                skuStockDao, skuStockMapper,
                productAttributeValueDao, productAttributeValueMapper,
                subjectProductRelationDao, subjectProductRelationMapper,
                prefrenceAreaProductRelationDao, prefrenceAreaProductRelationMapper,
                productDao, productVertifyRecordDao
        );
    }
    @Nested
    @DisplayName("getUpdateInfo - 获取商品编辑信息")
    class GetUpdateInfoTests {
        @Test
        @DisplayName("委托 productDao.getUpdateInfo")
        void getUpdateInfo_delegates() {
            var expected = new PmsProductResult();
            when(productDao.getUpdateInfo(1L)).thenReturn(expected);

            PmsProductResult result = productService.getUpdateInfo(1L);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("list(PmsProductQueryParam) - 分页查询商品")
    class ListWithParamsTests {
        @Test
        @DisplayName("无过滤条件时只过滤已删除商品")
        void list_noFilters_onlyExcludesDeleted() {
            var param = new PmsProductQueryParam(null, null, null, null, null, null);
            var expected = List.of(new PmsProduct());
            when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(expected);

            List<PmsProduct> result = productService.list(param, 10, 1);

            assertThat(result).isEqualTo(expected);
            verify(productMapper).selectByExample(any(PmsProductExample.class));
        }

        @Test
        @DisplayName("带关键字时添加 name LIKE 条件")
        void list_withKeyword_addsNameLike() {
            var param = new PmsProductQueryParam(null, null, "手机", null, null, null);
            when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(List.of());

            productService.list(param, 10, 1);

            verify(productMapper).selectByExample(any(PmsProductExample.class));
        }

        @Test
        @DisplayName("带品牌和分类时添加对应条件")
        void list_withBrandAndCategory_addsConditions() {
            var param = new PmsProductQueryParam(1, 1, null, "SN001", 5L, 3L);
            when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(List.of());

            productService.list(param, 20, 2);

            verify(productMapper).selectByExample(any(PmsProductExample.class));
        }
    }

    @Nested
    @DisplayName("list(String keyword) - 简单关键字查询")
    class ListWithKeywordTests {
        @Test
        @DisplayName("关键字为空时只过滤已删除")
        void list_emptyKeyword_onlyExcludesDeleted() {
            when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(List.of());

            productService.list("");

            verify(productMapper).selectByExample(any(PmsProductExample.class));
        }

        @Test
        @DisplayName("有关键字时按名称和货号模糊查询")
        void list_withKeyword_searchesNameAndSn() {
            when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(List.of());

            productService.list("iPhone");

            verify(productMapper).selectByExample(any(PmsProductExample.class));
        }
    }
    @Nested
    @DisplayName("updateVerifyStatus - 批量审核")
    class UpdateVerifyStatusTests {
        @Test
        @DisplayName("更新审核状态并插入审核记录")
        void updateVerifyStatus_updatesAndInsertsRecords() {
            var ids = List.of(1L, 2L, 3L);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                    .thenReturn(3);

            int count = productService.updateVerifyStatus(ids, 1, "审核通过");

            assertThat(count).isEqualTo(3);
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getVerifyStatus()).isEqualTo(1);
            verify(productVertifyRecordDao).insertList(vertifyRecordListCaptor.capture());
            List<PmsProductVertifyRecord> records = vertifyRecordListCaptor.getValue();
            assertThat(records).hasSize(3);
            assertThat(records.get(0).getProductId()).isEqualTo(1L);
            assertThat(records.get(0).getStatus()).isEqualTo(1);
            assertThat(records.get(0).getDetail()).isEqualTo("审核通过");
        }
    }

    @Nested
    @DisplayName("updatePublishStatus - 批量上下架")
    class UpdatePublishStatusTests {
        @Test
        @DisplayName("更新上架状态")
        void updatePublishStatus_updatesStatus() {
            var ids = List.of(1L, 2L);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                    .thenReturn(2);

            int count = productService.updatePublishStatus(ids, 1);

            assertThat(count).isEqualTo(2);
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getPublishStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateRecommendStatus - 批量推荐")
    class UpdateRecommendStatusTests {
        @Test
        @DisplayName("更新推荐状态")
        void updateRecommendStatus_updatesStatus() {
            var ids = List.of(5L);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                    .thenReturn(1);

            int count = productService.updateRecommendStatus(ids, 1);

            assertThat(count).isEqualTo(1);
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getRecommandStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateNewStatus - 批量设为新品")
    class UpdateNewStatusTests {
        @Test
        @DisplayName("更新新品状态")
        void updateNewStatus_updatesStatus() {
            var ids = List.of(10L, 11L);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                    .thenReturn(2);

            int count = productService.updateNewStatus(ids, 0);

            assertThat(count).isEqualTo(2);
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getNewStatus()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("updateDeleteStatus - 批量逻辑删除")
    class UpdateDeleteStatusTests {
        @Test
        @DisplayName("更新删除状态")
        void updateDeleteStatus_updatesStatus() {
            var ids = List.of(7L);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                    .thenReturn(1);

            int count = productService.updateDeleteStatus(ids, 1);

            assertThat(count).isEqualTo(1);
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getDeleteStatus()).isEqualTo(1);
        }
    }
}
