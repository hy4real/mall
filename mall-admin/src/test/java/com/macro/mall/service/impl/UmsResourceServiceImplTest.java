package com.macro.mall.service.impl;

import com.macro.mall.mapper.UmsResourceMapper;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsResourceExample;
import com.macro.mall.service.UmsAdminCacheService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsResourceServiceImpl 单元测试")
class UmsResourceServiceImplTest {

    @Mock private UmsResourceMapper resourceMapper;
    @Mock private UmsAdminCacheService adminCacheService;

    @Captor private ArgumentCaptor<UmsResource> resourceCaptor;

    private UmsResourceServiceImpl resourceService;

    @BeforeEach
    void setUp() {
        resourceService = new UmsResourceServiceImpl(resourceMapper, adminCacheService);
    }

    @Nested
    @DisplayName("create - 创建资源")
    class CreateTests {

        @Test
        @DisplayName("设置创建时间后插入")
        void create_setsCreateTimeAndInserts() {
            var resource = new UmsResource();
            resource.setName("/test/**");
            when(resourceMapper.insert(resource)).thenReturn(1);

            int count = resourceService.create(resource);

            assertThat(count).isEqualTo(1);
            assertThat(resource.getCreateTime()).isNotNull();
            verify(resourceMapper).insert(resource);
        }
    }

    @Nested
    @DisplayName("update - 更新资源")
    class UpdateTests {

        @Test
        @DisplayName("更新资源并清除缓存")
        void update_clearsCache() {
            var resource = new UmsResource();
            resource.setName("/updated/**");
            when(resourceMapper.updateByPrimaryKeySelective(resource)).thenReturn(1);

            int count = resourceService.update(5L, resource);

            assertThat(count).isEqualTo(1);
            assertThat(resource.getId()).isEqualTo(5L);
            verify(adminCacheService).delResourceListByResource(5L);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个资源")
    class GetItemTests {

        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new UmsResource();
            when(resourceMapper.selectByPrimaryKey(3L)).thenReturn(expected);

            var result = resourceService.getItem(3L);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("delete - 删除资源")
    class DeleteTests {

        @Test
        @DisplayName("删除后清除资源相关缓存")
        void delete_clearsResourceCache() {
            when(resourceMapper.deleteByPrimaryKey(7L)).thenReturn(1);

            int count = resourceService.delete(7L);

            assertThat(count).isEqualTo(1);
            verify(adminCacheService).delResourceListByResource(7L);
        }
    }

    @Nested
    @DisplayName("list - 分页查询资源")
    class ListTests {

        @Test
        @DisplayName("按分类ID过滤")
        void list_withCategoryId_filters() {
            var expected = List.of(new UmsResource());
            when(resourceMapper.selectByExample(any(UmsResourceExample.class))).thenReturn(expected);

            var result = resourceService.list(2L, null, null, 10, 1);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("无过滤条件时返回全部")
        void list_noFilters_returnsAll() {
            when(resourceMapper.selectByExample(any(UmsResourceExample.class))).thenReturn(List.of());

            var result = resourceService.list(null, null, null, 5, 1);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listAll - 获取全部资源")
    class ListAllTests {

        @Test
        @DisplayName("委托 mapper 查询全部")
        void listAll_delegates() {
            var list = List.of(new UmsResource(), new UmsResource());
            when(resourceMapper.selectByExample(any(UmsResourceExample.class))).thenReturn(list);

            var result = resourceService.listAll();

            assertThat(result).hasSize(2);
        }
    }
}
