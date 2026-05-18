package com.macro.mall.service.impl;

import com.macro.mall.mapper.UmsResourceCategoryMapper;
import com.macro.mall.model.UmsResourceCategory;
import com.macro.mall.model.UmsResourceCategoryExample;
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
@DisplayName("UmsResourceCategoryServiceImpl 单元测试")
class UmsResourceCategoryServiceImplTest {

    @Mock private UmsResourceCategoryMapper resourceCategoryMapper;
    @Captor private ArgumentCaptor<UmsResourceCategory> captor;
    private UmsResourceCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UmsResourceCategoryServiceImpl(resourceCategoryMapper);
    }

    @Nested
    @DisplayName("listAll - 获取所有资源分类")
    class ListAllTests {
        @Test
        @DisplayName("委托 mapper 查询全部")
        void listAll_delegates() {
            var list = List.of(new UmsResourceCategory());
            when(resourceCategoryMapper.selectByExample(any(UmsResourceCategoryExample.class))).thenReturn(list);

            assertThat(service.listAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("create - 创建资源分类")
    class CreateTests {
        @Test
        @DisplayName("设置创建时间后插入")
        void create_setsCreateTime() {
            var category = new UmsResourceCategory();
            when(resourceCategoryMapper.insert(category)).thenReturn(1);

            int count = service.create(category);

            assertThat(count).isEqualTo(1);
            assertThat(category.getCreateTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("update - 更新资源分类")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsId() {
            var category = new UmsResourceCategory();
            when(resourceCategoryMapper.updateByPrimaryKeySelective(category)).thenReturn(1);

            int count = service.update(5L, category);

            assertThat(count).isEqualTo(1);
            assertThat(category.getId()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("delete - 删除资源分类")
    class DeleteTests {
        @Test
        @DisplayName("委托 mapper 删除")
        void delete_delegates() {
            when(resourceCategoryMapper.deleteByPrimaryKey(3L)).thenReturn(1);

            assertThat(service.delete(3L)).isEqualTo(1);
        }
    }
}
