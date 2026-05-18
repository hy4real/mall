package com.macro.mall.service.impl;

import com.macro.mall.dao.UmsRoleDao;
import com.macro.mall.mapper.UmsRoleMapper;
import com.macro.mall.mapper.UmsRoleMenuRelationMapper;
import com.macro.mall.mapper.UmsRoleResourceRelationMapper;
import com.macro.mall.model.*;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsRoleServiceImpl 单元测试")
class UmsRoleServiceImplTest {

    @Mock private UmsRoleMapper roleMapper;
    @Mock private UmsRoleMenuRelationMapper roleMenuRelationMapper;
    @Mock private UmsRoleResourceRelationMapper roleResourceRelationMapper;
    @Mock private UmsRoleDao roleDao;
    @Mock private UmsAdminCacheService adminCacheService;

    @Captor private ArgumentCaptor<UmsRole> roleCaptor;
    @Captor private ArgumentCaptor<UmsRoleMenuRelation> menuRelationCaptor;
    @Captor private ArgumentCaptor<UmsRoleResourceRelation> resourceRelationCaptor;

    private UmsRoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new UmsRoleServiceImpl(
                roleMapper, roleMenuRelationMapper, roleResourceRelationMapper,
                roleDao, adminCacheService);
    }

    @Nested
    @DisplayName("create - 创建角色")
    class CreateTests {
        @Test
        @DisplayName("设置默认值并插入")
        void create_setsDefaultsAndInserts() {
            when(roleMapper.insert(any(UmsRole.class))).thenReturn(1);

            UmsRole role = new UmsRole();
            role.setName("商品管理员");
            int count = roleService.create(role);

            assertThat(count).isEqualTo(1);
            verify(roleMapper).insert(roleCaptor.capture());
            assertThat(roleCaptor.getValue().getCreateTime()).isNotNull();
            assertThat(roleCaptor.getValue().getAdminCount()).isEqualTo(0);
            assertThat(roleCaptor.getValue().getSort()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("update - 更新角色")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsIdAndUpdates() {
            when(roleMapper.updateByPrimaryKeySelective(any(UmsRole.class))).thenReturn(1);

            UmsRole role = new UmsRole();
            role.setName("新名称");
            int count = roleService.update(5L, role);

            assertThat(count).isEqualTo(1);
            verify(roleMapper).updateByPrimaryKeySelective(roleCaptor.capture());
            assertThat(roleCaptor.getValue().getId()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("delete - 删除角色")
    class DeleteTests {
        @Test
        @DisplayName("批量删除并清除缓存")
        void delete_deletesAndClearsCache() {
            var ids = List.of(1L, 2L);
            when(roleMapper.deleteByExample(any(UmsRoleExample.class))).thenReturn(2);

            int count = roleService.delete(ids);

            assertThat(count).isEqualTo(2);
            verify(adminCacheService).delResourceListByRoleIds(ids);
        }
    }

    @Nested
    @DisplayName("allocMenu - 分配菜单")
    class AllocMenuTests {
        @Test
        @DisplayName("先删除旧关系再批量插入新关系")
        void allocMenu_replacesRelations() {
            var menuIds = List.of(10L, 20L, 30L);
            when(roleMenuRelationMapper.insert(any(UmsRoleMenuRelation.class))).thenReturn(1);

            int count = roleService.allocMenu(1L, menuIds);

            assertThat(count).isEqualTo(3);
            verify(roleMenuRelationMapper).deleteByExample(any(UmsRoleMenuRelationExample.class));
            verify(roleMenuRelationMapper, times(3)).insert(menuRelationCaptor.capture());
            List<UmsRoleMenuRelation> relations = menuRelationCaptor.getAllValues();
            assertThat(relations.get(0).getRoleId()).isEqualTo(1L);
            assertThat(relations.get(0).getMenuId()).isEqualTo(10L);
            assertThat(relations.get(2).getMenuId()).isEqualTo(30L);
        }
    }

    @Nested
    @DisplayName("allocResource - 分配资源")
    class AllocResourceTests {
        @Test
        @DisplayName("先删除旧关系再批量插入新关系并清除缓存")
        void allocResource_replacesRelationsAndClearsCache() {
            var resourceIds = List.of(100L, 200L);
            when(roleResourceRelationMapper.insert(any(UmsRoleResourceRelation.class))).thenReturn(1);

            int count = roleService.allocResource(3L, resourceIds);

            assertThat(count).isEqualTo(2);
            verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
            verify(roleResourceRelationMapper, times(2)).insert(resourceRelationCaptor.capture());
            List<UmsRoleResourceRelation> relations = resourceRelationCaptor.getAllValues();
            assertThat(relations.get(0).getRoleId()).isEqualTo(3L);
            assertThat(relations.get(0).getResourceId()).isEqualTo(100L);
            verify(adminCacheService).delResourceListByRole(3L);
        }
    }

    @Nested
    @DisplayName("getMenuList - 获取菜单列表")
    class GetMenuListTests {
        @Test
        @DisplayName("委托 roleDao.getMenuList")
        void getMenuList_delegates() {
            var expected = List.of(new UmsMenu());
            when(roleDao.getMenuList(1L)).thenReturn(expected);

            List<UmsMenu> result = roleService.getMenuList(1L);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("listMenu - 按角色获取菜单")
    class ListMenuTests {
        @Test
        @DisplayName("委托 roleDao.getMenuListByRoleId")
        void listMenu_delegates() {
            var expected = List.of(new UmsMenu());
            when(roleDao.getMenuListByRoleId(5L)).thenReturn(expected);

            List<UmsMenu> result = roleService.listMenu(5L);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("listResource - 按角色获取资源")
    class ListResourceTests {
        @Test
        @DisplayName("委托 roleDao.getResourceListByRoleId")
        void listResource_delegates() {
            var expected = List.of(new UmsResource());
            when(roleDao.getResourceListByRoleId(5L)).thenReturn(expected);

            List<UmsResource> result = roleService.listResource(5L);

            assertThat(result).isEqualTo(expected);
        }
    }
}
