package com.macro.mall.service.impl;

import com.macro.mall.dao.PmsProductCategoryAttributeRelationDao;
import com.macro.mall.dao.PmsProductCategoryDao;
import com.macro.mall.dto.PmsProductCategoryParam;
import com.macro.mall.dto.PmsProductCategoryWithChildrenItem;
import com.macro.mall.mapper.PmsProductCategoryAttributeRelationMapper;
import com.macro.mall.mapper.PmsProductCategoryMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.*;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmsProductCategoryServiceImpl 单元测试")
class PmsProductCategoryServiceImplTest {

    @Mock private PmsProductCategoryMapper productCategoryMapper;
    @Mock private PmsProductMapper productMapper;
    @Mock private PmsProductCategoryAttributeRelationDao productCategoryAttributeRelationDao;
    @Mock private PmsProductCategoryAttributeRelationMapper productCategoryAttributeRelationMapper;
    @Mock private PmsProductCategoryDao productCategoryDao;

    @Captor private ArgumentCaptor<PmsProductCategory> categoryCaptor;
    @Captor private ArgumentCaptor<PmsProduct> productCaptor;

    private PmsProductCategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new PmsProductCategoryServiceImpl(
                productCategoryMapper, productMapper,
                productCategoryAttributeRelationDao, productCategoryAttributeRelationMapper,
                productCategoryDao);
    }

    @Nested
    @DisplayName("create - 创建商品分类")
    class CreateTests {
        @Test
        @DisplayName("一级分类设置 level=0 并插入关联属性")
        void create_topLevel_setsLevelAndInsertsRelations() {
            var param = new PmsProductCategoryParam(0L, "手机配件", "件", 1, 1, 0, "icon.png", "手机", "手机相关", List.of(100L, 200L));
            when(productCategoryMapper.insertSelective(any(PmsProductCategory.class))).thenAnswer(invocation -> {
                PmsProductCategory cat = invocation.getArgument(0);
                cat.setId(1L);
                return 1;
            });

            int count = categoryService.create(param);

            assertThat(count).isEqualTo(1);
            verify(productCategoryMapper).insertSelective(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getLevel()).isEqualTo(0);
            assertThat(categoryCaptor.getValue().getProductCount()).isEqualTo(0);
            verify(productCategoryAttributeRelationDao).insertList(anyList());
        }

        @Test
        @DisplayName("子分类根据父分类 level+1")
        void create_childCategory_inheritsParentLevel() {
            PmsProductCategory parent = new PmsProductCategory();
            parent.setLevel(0);
            when(productCategoryMapper.selectByPrimaryKey(5L)).thenReturn(parent);
            when(productCategoryMapper.insertSelective(any(PmsProductCategory.class))).thenReturn(1);

            var param = new PmsProductCategoryParam(5L, "手机壳", "个", 1, 1, 0, null, null, null, null);
            categoryService.create(param);

            verify(productCategoryMapper).insertSelective(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("无属性列表时不插入关联")
        void create_noAttributes_skipsRelationInsert() {
            var param = new PmsProductCategoryParam(0L, "测试分类", "件", 1, 1, 0, null, null, null, null);
            when(productCategoryMapper.insertSelective(any(PmsProductCategory.class))).thenReturn(1);

            categoryService.create(param);

            verify(productCategoryAttributeRelationDao, never()).insertList(anyList());
        }
    }

    @Nested
    @DisplayName("update - 更新商品分类")
    class UpdateTests {
        @Test
        @DisplayName("更新分类并同步商品的分类名称")
        void update_updatesCategoryAndProductCategoryName() {
            var param = new PmsProductCategoryParam(0L, "新名称", "件", 1, 1, 0, null, null, null, List.of(1L));
            when(productCategoryMapper.updateByPrimaryKeySelective(any(PmsProductCategory.class))).thenReturn(1);
            when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class))).thenReturn(0);

            int count = categoryService.update(10L, param);

            assertThat(count).isEqualTo(1);
            verify(productCategoryMapper).updateByPrimaryKeySelective(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getId()).isEqualTo(10L);
            assertThat(categoryCaptor.getValue().getName()).isEqualTo("新名称");
            verify(productMapper).updateByExampleSelective(productCaptor.capture(), any(PmsProductExample.class));
            assertThat(productCaptor.getValue().getProductCategoryName()).isEqualTo("新名称");
        }
    }

    @Nested
    @DisplayName("delete - 删除分类")
    class DeleteTests {
        @Test
        @DisplayName("按 ID 删除")
        void delete_delegates() {
            when(productCategoryMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            int count = categoryService.delete(5L);

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getItem - 获取分类详情")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper.selectByPrimaryKey")
        void getItem_delegates() {
            var expected = new PmsProductCategory();
            when(productCategoryMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            PmsProductCategory result = categoryService.getItem(1L);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("updateNavStatus - 批量修改导航状态")
    class UpdateNavStatusTests {
        @Test
        @DisplayName("更新指定 ID 的导航状态")
        void updateNavStatus_delegates() {
            var ids = List.of(1L, 2L);
            when(productCategoryMapper.updateByExampleSelective(any(PmsProductCategory.class), any(PmsProductCategoryExample.class))).thenReturn(2);

            int count = categoryService.updateNavStatus(ids, 1);

            assertThat(count).isEqualTo(2);
            verify(productCategoryMapper).updateByExampleSelective(categoryCaptor.capture(), any(PmsProductCategoryExample.class));
            assertThat(categoryCaptor.getValue().getNavStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateShowStatus - 批量修改显示状态")
    class UpdateShowStatusTests {
        @Test
        @DisplayName("更新指定 ID 的显示状态")
        void updateShowStatus_delegates() {
            var ids = List.of(1L, 2L);
            when(productCategoryMapper.updateByExampleSelective(any(PmsProductCategory.class), any(PmsProductCategoryExample.class))).thenReturn(2);

            int count = categoryService.updateShowStatus(ids, 0);

            assertThat(count).isEqualTo(2);
            verify(productCategoryMapper).updateByExampleSelective(categoryCaptor.capture(), any(PmsProductCategoryExample.class));
            assertThat(categoryCaptor.getValue().getShowStatus()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("listWithChildren - 获取带子分类的列表")
    class ListWithChildrenTests {
        @Test
        @DisplayName("委托 productCategoryDao.listWithChildren")
        void listWithChildren_delegates() {
            var expected = List.of(new PmsProductCategoryWithChildrenItem());
            when(productCategoryDao.listWithChildren()).thenReturn(expected);

            var result = categoryService.listWithChildren();

            assertThat(result).isEqualTo(expected);
        }
    }
}
