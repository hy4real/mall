package com.macro.mall.service.impl;

import com.macro.mall.dao.PmsProductAttributeDao;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.dto.ProductAttrInfo;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.model.PmsProductAttributeExample;
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
@DisplayName("PmsProductAttributeServiceImpl 单元测试")
class PmsProductAttributeServiceImplTest {

    @Mock private PmsProductAttributeMapper productAttributeMapper;
    @Mock private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    @Mock private PmsProductAttributeDao productAttributeDao;

    @Captor private ArgumentCaptor<PmsProductAttribute> attributeCaptor;
    @Captor private ArgumentCaptor<PmsProductAttributeCategory> categoryCaptor;

    private PmsProductAttributeServiceImpl attributeService;

    @BeforeEach
    void setUp() {
        attributeService = new PmsProductAttributeServiceImpl(
                productAttributeMapper, productAttributeCategoryMapper, productAttributeDao);
    }

    @Nested
    @DisplayName("create - 创建属性")
    class CreateTests {
        @Test
        @DisplayName("type=0 时增加 attributeCount")
        void create_type0_incrementsAttributeCount() {
            var param = new PmsProductAttributeParam(1L, "颜色", null, null, null, 0, 0, 0, 0, 0, 0);
            var category = new PmsProductAttributeCategory();
            category.setAttributeCount(5);
            category.setParamCount(3);
            when(productAttributeMapper.insertSelective(any(PmsProductAttribute.class))).thenReturn(1);
            when(productAttributeCategoryMapper.selectByPrimaryKey(1L)).thenReturn(category);

            int count = attributeService.create(param);

            assertThat(count).isEqualTo(1);
            verify(productAttributeCategoryMapper).updateByPrimaryKey(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getAttributeCount()).isEqualTo(6);
            assertThat(categoryCaptor.getValue().getParamCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("type=1 时增加 paramCount")
        void create_type1_incrementsParamCount() {
            var param = new PmsProductAttributeParam(1L, "尺寸", null, null, null, 0, 0, 0, 0, 0, 1);
            var category = new PmsProductAttributeCategory();
            category.setAttributeCount(5);
            category.setParamCount(3);
            when(productAttributeMapper.insertSelective(any(PmsProductAttribute.class))).thenReturn(1);
            when(productAttributeCategoryMapper.selectByPrimaryKey(1L)).thenReturn(category);

            attributeService.create(param);

            verify(productAttributeCategoryMapper).updateByPrimaryKey(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getParamCount()).isEqualTo(4);
            assertThat(categoryCaptor.getValue().getAttributeCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("update - 更新属性")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsIdAndUpdates() {
            var param = new PmsProductAttributeParam(1L, "新名称", null, null, null, 0, 0, 0, 0, 0, 0);
            when(productAttributeMapper.updateByPrimaryKeySelective(any(PmsProductAttribute.class))).thenReturn(1);

            int count = attributeService.update(10L, param);

            assertThat(count).isEqualTo(1);
            verify(productAttributeMapper).updateByPrimaryKeySelective(attributeCaptor.capture());
            assertThat(attributeCaptor.getValue().getId()).isEqualTo(10L);
            assertThat(attributeCaptor.getValue().getName()).isEqualTo("新名称");
        }
    }

    @Nested
    @DisplayName("delete - 删除属性")
    class DeleteTests {
        @Test
        @DisplayName("type=0 时减少 attributeCount")
        void delete_type0_decrementsAttributeCount() {
            var attribute = new PmsProductAttribute();
            attribute.setType(0);
            attribute.setProductAttributeCategoryId(1L);
            var category = new PmsProductAttributeCategory();
            category.setAttributeCount(5);
            when(productAttributeMapper.selectByPrimaryKey(1L)).thenReturn(attribute);
            when(productAttributeCategoryMapper.selectByPrimaryKey(1L)).thenReturn(category);
            when(productAttributeMapper.deleteByExample(any(PmsProductAttributeExample.class))).thenReturn(2);

            int count = attributeService.delete(List.of(1L, 2L));

            assertThat(count).isEqualTo(2);
            verify(productAttributeCategoryMapper).updateByPrimaryKey(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getAttributeCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("type=1 时减少 paramCount，不低于 0")
        void delete_type1_decrementsParamCountNotBelowZero() {
            var attribute = new PmsProductAttribute();
            attribute.setType(1);
            attribute.setProductAttributeCategoryId(1L);
            var category = new PmsProductAttributeCategory();
            category.setParamCount(1);
            when(productAttributeMapper.selectByPrimaryKey(10L)).thenReturn(attribute);
            when(productAttributeCategoryMapper.selectByPrimaryKey(1L)).thenReturn(category);
            when(productAttributeMapper.deleteByExample(any(PmsProductAttributeExample.class))).thenReturn(5);

            attributeService.delete(List.of(10L, 11L));

            verify(productAttributeCategoryMapper).updateByPrimaryKey(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getParamCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getItem - 获取属性详情")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper.selectByPrimaryKey")
        void getItem_delegates() {
            var expected = new PmsProductAttribute();
            when(productAttributeMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            PmsProductAttribute result = attributeService.getItem(1L);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getProductAttrInfo - 获取分类属性信息")
    class GetProductAttrInfoTests {
        @Test
        @DisplayName("委托 productAttributeDao.getProductAttrInfo")
        void getProductAttrInfo_delegates() {
            var expected = List.of(new ProductAttrInfo());
            when(productAttributeDao.getProductAttrInfo(1L)).thenReturn(expected);

            var result = attributeService.getProductAttrInfo(1L);

            assertThat(result).isEqualTo(expected);
        }
    }
}
