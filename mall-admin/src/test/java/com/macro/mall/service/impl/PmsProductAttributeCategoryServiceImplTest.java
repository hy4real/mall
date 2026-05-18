package com.macro.mall.service.impl;

import com.macro.mall.dao.PmsProductAttributeCategoryDao;
import com.macro.mall.dto.PmsProductAttributeCategoryItem;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.model.PmsProductAttributeCategory;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmsProductAttributeCategoryServiceImpl 单元测试")
class PmsProductAttributeCategoryServiceImplTest {

    @Mock private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    @Mock private PmsProductAttributeCategoryDao productAttributeCategoryDao;
    @Captor private ArgumentCaptor<PmsProductAttributeCategory> captor;
    private PmsProductAttributeCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PmsProductAttributeCategoryServiceImpl(productAttributeCategoryMapper, productAttributeCategoryDao);
    }

    @Nested
    @DisplayName("create - 创建属性分类")
    class CreateTests {
        @Test
        @DisplayName("根据名称构造实体并插入")
        void create_constructsEntity() {
            when(productAttributeCategoryMapper.insertSelective(any())).thenReturn(1);

            int count = service.create("颜色");

            assertThat(count).isEqualTo(1);
            verify(productAttributeCategoryMapper).insertSelective(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("颜色");
        }
    }

    @Nested
    @DisplayName("update - 更新属性分类")
    class UpdateTests {
        @Test
        @DisplayName("根据 ID 和名称构造实体并更新")
        void update_constructsEntity() {
            when(productAttributeCategoryMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = service.update(3L, "尺码");

            assertThat(count).isEqualTo(1);
            verify(productAttributeCategoryMapper).updateByPrimaryKeySelective(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(3L);
            assertThat(captor.getValue().getName()).isEqualTo("尺码");
        }
    }

    @Nested
    @DisplayName("delete - 删除属性分类")
    class DeleteTests {
        @Test
        @DisplayName("委托 mapper 删除")
        void delete_delegates() {
            when(productAttributeCategoryMapper.deleteByPrimaryKey(2L)).thenReturn(1);

            assertThat(service.delete(2L)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个属性分类")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new PmsProductAttributeCategory();
            when(productAttributeCategoryMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getList - 分页查询属性分类列表")
    class GetListTests {
        @Test
        @DisplayName("分页委托 mapper 查询")
        void getList_paginates() {
            var list = List.of(new PmsProductAttributeCategory());
            when(productAttributeCategoryMapper.selectByExample(any())).thenReturn(list);

            assertThat(service.getList(10, 1)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getListWithAttr - 查询带属性的分类")
    class GetListWithAttrTests {
        @Test
        @DisplayName("委托 DAO 查询")
        void getListWithAttr_delegates() {
            var items = List.of(new PmsProductAttributeCategoryItem());
            when(productAttributeCategoryDao.getListWithAttr()).thenReturn(items);

            assertThat(service.getListWithAttr()).hasSize(1);
        }
    }
}
