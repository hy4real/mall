package com.macro.mall.service.impl;

import com.macro.mall.dto.PmsBrandParam;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsBrandExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmsBrandServiceImpl 单元测试")
class PmsBrandServiceImplTest {

    @Mock private PmsBrandMapper brandMapper;
    @Mock private PmsProductMapper productMapper;

    @Captor private ArgumentCaptor<PmsBrand> brandCaptor;
    @Captor private ArgumentCaptor<PmsBrandExample> brandExampleCaptor;
    @Captor private ArgumentCaptor<PmsProduct> productCaptor;

    private PmsBrandServiceImpl brandService;

    @BeforeEach
    void setUp() {
        brandService = new PmsBrandServiceImpl(brandMapper, productMapper);
    }

    @Nested
    @DisplayName("listAllBrand - 获取全部品牌")
    class ListAllBrandTests {
        @Test
        @DisplayName("委托 brandMapper.selectByExample")
        void listAllBrand_delegates() {
            var expected = List.of(new PmsBrand());
            when(brandMapper.selectByExample(any(PmsBrandExample.class))).thenReturn(expected);

            List<PmsBrand> result = brandService.listAllBrand();

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("createBrand - 创建品牌")
    class CreateBrandTests {
        @Test
        @DisplayName("首字母为空时取名称首字符")
        void createBrand_emptyFirstLetter_usesNameFirstChar() {
            var param = new PmsBrandParam("华为", null, 0, 1, 1, "logo.png", null, null);
            when(brandMapper.insertSelective(any(PmsBrand.class))).thenReturn(1);

            brandService.createBrand(param);

            verify(brandMapper).insertSelective(brandCaptor.capture());
            assertThat(brandCaptor.getValue().getFirstLetter()).isEqualTo("华");
            assertThat(brandCaptor.getValue().getName()).isEqualTo("华为");
        }

        @Test
        @DisplayName("首字母已提供时直接使用")
        void createBrand_withFirstLetter_usesProvided() {
            var param = new PmsBrandParam("Apple", "A", 0, 1, 1, "logo.png", null, null);
            when(brandMapper.insertSelective(any(PmsBrand.class))).thenReturn(1);

            brandService.createBrand(param);

            verify(brandMapper).insertSelective(brandCaptor.capture());
            assertThat(brandCaptor.getValue().getFirstLetter()).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("updateBrand - 更新品牌")
    class UpdateBrandTests {
        @Test
        @DisplayName("更新品牌并同步更新关联商品的 brandName")
        void updateBrand_updatesBrandAndProductBrandName() {
            var param = new PmsBrandParam("新品牌", "X", 0, 1, 1, "logo.png", null, null);
            when(brandMapper.updateByPrimaryKeySelective(any(PmsBrand.class))).thenReturn(1);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class))).thenReturn(3);

            int count = brandService.updateBrand(10L, param);

            assertThat(count).isEqualTo(1);
            verify(brandMapper).updateByPrimaryKeySelective(brandCaptor.capture());
            assertThat(brandCaptor.getValue().getId()).isEqualTo(10L);
            assertThat(brandCaptor.getValue().getName()).isEqualTo("新品牌");
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getBrandName()).isEqualTo("新品牌");
        }
    }

    @Nested
    @DisplayName("deleteBrand - 删除品牌")
    class DeleteBrandTests {
        @Test
        @DisplayName("按单个 ID 删除")
        void deleteBrand_singleId_delegates() {
            when(brandMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            int count = brandService.deleteBrand(5L);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("按 ID 列表批量删除")
        void deleteBrand_batch_delegates() {
            var ids = List.of(1L, 2L, 3L);
            when(brandMapper.deleteByExample(any(PmsBrandExample.class))).thenReturn(3);

            int count = brandService.deleteBrand(ids);

            assertThat(count).isEqualTo(3);
            verify(brandMapper).deleteByExample(brandExampleCaptor.capture());
        }
    }

    @Nested
    @DisplayName("updateShowStatus - 批量修改显示状态")
    class UpdateShowStatusTests {
        @Test
        @DisplayName("更新指定 ID 的显示状态")
        void updateShowStatus_delegates() {
            var ids = List.of(1L, 2L);
            when(brandMapper.updateByExampleSelective(any(PmsBrand.class), any(PmsBrandExample.class))).thenReturn(2);

            int count = brandService.updateShowStatus(ids, 1);

            assertThat(count).isEqualTo(2);
            verify(brandMapper).updateByExampleSelective(brandCaptor.capture(), any(PmsBrandExample.class));
            assertThat(brandCaptor.getValue().getShowStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateFactoryStatus - 批量修改厂家状态")
    class UpdateFactoryStatusTests {
        @Test
        @DisplayName("更新指定 ID 的厂家状态")
        void updateFactoryStatus_delegates() {
            var ids = List.of(1L, 2L);
            when(brandMapper.updateByExampleSelective(any(PmsBrand.class), any(PmsBrandExample.class))).thenReturn(2);

            int count = brandService.updateFactoryStatus(ids, 0);

            assertThat(count).isEqualTo(2);
            verify(brandMapper).updateByExampleSelective(brandCaptor.capture(), any(PmsBrandExample.class));
            assertThat(brandCaptor.getValue().getFactoryStatus()).isEqualTo(0);
        }
    }
}
