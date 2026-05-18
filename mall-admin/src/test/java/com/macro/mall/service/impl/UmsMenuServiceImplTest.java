package com.macro.mall.service.impl;

import com.macro.mall.dto.UmsMenuNode;
import com.macro.mall.mapper.UmsMenuMapper;
import com.macro.mall.model.UmsMenu;
import com.macro.mall.model.UmsMenuExample;
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
@DisplayName("UmsMenuServiceImpl 单元测试")
class UmsMenuServiceImplTest {

    @Mock private UmsMenuMapper menuMapper;

    @Captor private ArgumentCaptor<UmsMenu> menuCaptor;

    private UmsMenuServiceImpl menuService;

    @BeforeEach
    void setUp() {
        menuService = new UmsMenuServiceImpl(menuMapper);
    }

    @Nested
    @DisplayName("create - 创建菜单")
    class CreateTests {

        @Test
        @DisplayName("父菜单ID为0时设为一级菜单")
        void create_rootMenu_setsLevelZero() {
            var menu = new UmsMenu();
            menu.setName("系统管理");
            menu.setParentId(0L);
            when(menuMapper.insert(menu)).thenReturn(1);

            int count = menuService.create(menu);

            assertThat(count).isEqualTo(1);
            assertThat(menu.getLevel()).isEqualTo(0);
            assertThat(menu.getCreateTime()).isNotNull();
        }

        @Test
        @DisplayName("子菜单继承父菜单层级+1")
        void create_childMenu_incrementsLevel() {
            var parent = new UmsMenu();
            parent.setId(10L);
            parent.setLevel(1);
            when(menuMapper.selectByPrimaryKey(10L)).thenReturn(parent);

            var menu = new UmsMenu();
            menu.setName("菜单列表");
            menu.setParentId(10L);
            when(menuMapper.insert(menu)).thenReturn(1);

            menuService.create(menu);

            assertThat(menu.getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("父菜单不存在时设为一级菜单")
        void create_missingParent_setsLevelZero() {
            when(menuMapper.selectByPrimaryKey(99L)).thenReturn(null);

            var menu = new UmsMenu();
            menu.setName("孤儿菜单");
            menu.setParentId(99L);
            when(menuMapper.insert(menu)).thenReturn(1);

            menuService.create(menu);

            assertThat(menu.getLevel()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("update - 更新菜单")
    class UpdateTests {

        @Test
        @DisplayName("更新菜单并重新计算层级")
        void update_recalculatesLevel() {
            var menu = new UmsMenu();
            menu.setName("更新菜单");
            menu.setParentId(0L);
            when(menuMapper.updateByPrimaryKeySelective(menu)).thenReturn(1);

            int count = menuService.update(5L, menu);

            assertThat(count).isEqualTo(1);
            assertThat(menu.getId()).isEqualTo(5L);
            assertThat(menu.getLevel()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个菜单")
    class GetItemTests {

        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new UmsMenu();
            when(menuMapper.selectByPrimaryKey(3L)).thenReturn(expected);

            var result = menuService.getItem(3L);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("delete - 删除菜单")
    class DeleteTests {

        @Test
        @DisplayName("委托 mapper 删除")
        void delete_delegates() {
            when(menuMapper.deleteByPrimaryKey(7L)).thenReturn(1);

            int count = menuService.delete(7L);

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("list - 分页查询子菜单")
    class ListTests {

        @Test
        @DisplayName("按父菜单ID查询子菜单")
        void list_byParentId_delegates() {
            var expected = List.of(new UmsMenu(), new UmsMenu());
            when(menuMapper.selectByExample(any(UmsMenuExample.class))).thenReturn(expected);

            var result = menuService.list(0L, 10, 1);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("treeList - 构建菜单树")
    class TreeListTests {

        @Test
        @DisplayName("构建多层菜单树")
        void treeList_buildsTree() {
            var root1 = menuWithId(1L, 0L);
            var child1 = menuWithId(2L, 1L);
            var child2 = menuWithId(3L, 1L);
            var grandchild = menuWithId(4L, 2L);
            var allMenus = List.of(root1, child1, child2, grandchild);
            when(menuMapper.selectByExample(any(UmsMenuExample.class))).thenReturn(allMenus);

            List<UmsMenuNode> tree = menuService.treeList();

            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).getId()).isEqualTo(1L);
            assertThat(tree.get(0).getChildren()).hasSize(2);
            var children = tree.get(0).getChildren();
            assertThat(children.get(0).getChildren()).hasSize(1); // child with id=2 has one sub
            assertThat(children.get(1).getChildren()).isEmpty();  // child with id=3 has none
        }

        @Test
        @DisplayName("空菜单列表返回空树")
        void treeList_empty_returnsEmpty() {
            when(menuMapper.selectByExample(any(UmsMenuExample.class))).thenReturn(List.of());

            List<UmsMenuNode> tree = menuService.treeList();

            assertThat(tree).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateHidden - 修改菜单显示状态")
    class UpdateHiddenTests {

        @Test
        @DisplayName("更新隐藏状态")
        void updateHidden_setsHidden() {
            when(menuMapper.updateByPrimaryKeySelective(any(UmsMenu.class))).thenReturn(1);

            int count = menuService.updateHidden(5L, 1);

            assertThat(count).isEqualTo(1);
            verify(menuMapper).updateByPrimaryKeySelective(menuCaptor.capture());
            assertThat(menuCaptor.getValue().getId()).isEqualTo(5L);
            assertThat(menuCaptor.getValue().getHidden()).isEqualTo(1);
        }
    }

    private static UmsMenu menuWithId(Long id, Long parentId) {
        var menu = new UmsMenu();
        menu.setId(id);
        menu.setName("菜单" + id);
        menu.setParentId(parentId);
        return menu;
    }
}
