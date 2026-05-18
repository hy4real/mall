package com.macro.mall.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.dto.UmsAdminParam;
import com.macro.mall.dto.UpdateAdminPasswordParam;
import com.macro.mall.mapper.UmsAdminLoginLogMapper;
import com.macro.mall.mapper.UmsAdminMapper;
import com.macro.mall.mapper.UmsAdminRoleRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.security.util.JwtTokenUtil;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsAdminServiceImpl 单元测试")
class UmsAdminServiceImplTest {

    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UmsAdminMapper adminMapper;
    @Mock private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Mock private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Mock private UmsAdminLoginLogMapper loginLogMapper;
    @Mock private UmsAdminCacheService cacheService;

    @Captor private ArgumentCaptor<UmsAdmin> adminCaptor;
    @Captor private ArgumentCaptor<List<UmsAdminRoleRelation>> roleRelationListCaptor;

    private UmsAdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        adminService = spy(new UmsAdminServiceImpl(
                jwtTokenUtil, passwordEncoder, adminMapper,
                adminRoleRelationMapper, adminRoleRelationDao, loginLogMapper));
        lenient().doReturn(cacheService).when(adminService).getCacheService();
    }
    private UmsAdmin createAdmin(Long id, String username, String password) {
        UmsAdmin admin = new UmsAdmin();
        admin.setId(id);
        admin.setUsername(username);
        admin.setPassword(password);
        admin.setStatus(1);
        return admin;
    }

    @Nested
    @DisplayName("getAdminByUsername - 根据用户名获取用户")
    class GetAdminByUsernameTests {
        @Test
        @DisplayName("缓存命中时直接返回")
        void getAdminByUsername_cacheHit_returnsCached() {
            UmsAdmin cached = createAdmin(1L, "admin", "encoded");
            when(cacheService.getAdmin("admin")).thenReturn(cached);

            UmsAdmin result = adminService.getAdminByUsername("admin");

            assertThat(result).isSameAs(cached);
            verifyNoInteractions(adminMapper);
        }

        @Test
        @DisplayName("缓存未命中时查数据库并写入缓存")
        void getAdminByUsername_cacheMiss_queriesDbAndCaches() {
            UmsAdmin dbAdmin = createAdmin(1L, "admin", "encoded");
            when(cacheService.getAdmin("admin")).thenReturn(null);
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(List.of(dbAdmin));

            UmsAdmin result = adminService.getAdminByUsername("admin");

            assertThat(result).isSameAs(dbAdmin);
            verify(cacheService).setAdmin(dbAdmin);
        }

        @Test
        @DisplayName("数据库中不存在时返回 null")
        void getAdminByUsername_notFound_returnsNull() {
            when(cacheService.getAdmin("ghost")).thenReturn(null);
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(Collections.emptyList());

            assertThat(adminService.getAdminByUsername("ghost")).isNull();
        }
    }

    @Nested
    @DisplayName("register - 注册新用户")
    class RegisterTests {
        @Test
        @DisplayName("用户名不重复时注册成功")
        void register_uniqueUsername_succeeds() {
            var param = new UmsAdminParam("newuser", "pass123", null, "a@b.com", "Nick", null);
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(Collections.emptyList());
            when(passwordEncoder.encode("pass123")).thenReturn("$encoded$");

            UmsAdmin result = adminService.register(param);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("newuser");
            assertThat(result.getPassword()).isEqualTo("$encoded$");
            assertThat(result.getStatus()).isEqualTo(1);
            verify(adminMapper).insert(any(UmsAdmin.class));
        }

        @Test
        @DisplayName("用户名已存在时返回 null")
        void register_duplicateUsername_returnsNull() {
            var param = new UmsAdminParam("existing", "pass", null, null, null, null);
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(List.of(new UmsAdmin()));

            assertThat(adminService.register(param)).isNull();
            verify(adminMapper, never()).insert(any());
        }
    }
    @Nested
    @DisplayName("update - 更新用户信息")
    class UpdateTests {
        @Test
        @DisplayName("密码未变时不重新加密")
        void update_samePassword_doesNotReEncode() {
            UmsAdmin existing = createAdmin(1L, "admin", "$encoded$");
            when(adminMapper.selectByPrimaryKey(1L)).thenReturn(existing);

            UmsAdmin updateParam = new UmsAdmin();
            updateParam.setPassword("$encoded$");
            updateParam.setNickName("NewNick");
            when(adminMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            int count = adminService.update(1L, updateParam);

            assertThat(count).isEqualTo(1);
            verify(adminMapper).updateByPrimaryKeySelective(adminCaptor.capture());
            assertThat(adminCaptor.getValue().getPassword()).isNull();
            verify(cacheService).delAdmin(1L);
        }

        @Test
        @DisplayName("密码变更时重新加密")
        void update_differentPassword_reEncodes() {
            UmsAdmin existing = createAdmin(1L, "admin", "$old$");
            when(adminMapper.selectByPrimaryKey(1L)).thenReturn(existing);

            UmsAdmin updateParam = new UmsAdmin();
            updateParam.setPassword("newPlaintext");
            when(passwordEncoder.encode("newPlaintext")).thenReturn("$new$");
            when(adminMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            adminService.update(1L, updateParam);

            verify(adminMapper).updateByPrimaryKeySelective(adminCaptor.capture());
            assertThat(adminCaptor.getValue().getPassword()).isEqualTo("$new$");
        }
    }

    @Nested
    @DisplayName("delete - 删除用户")
    class DeleteTests {
        @Test
        @DisplayName("删除用户并清除缓存")
        void delete_clearsCache() {
            when(adminMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            int count = adminService.delete(5L);

            assertThat(count).isEqualTo(1);
            verify(cacheService).delAdmin(5L);
            verify(cacheService).delResourceList(5L);
        }
    }

    @Nested
    @DisplayName("updateRole - 更新用户角色")
    class UpdateRoleTests {
        @Test
        @DisplayName("删除旧关系并批量插入新关系")
        void updateRole_replacesRelations() {
            var roleIds = List.of(10L, 20L);

            int count = adminService.updateRole(1L, roleIds);

            assertThat(count).isEqualTo(2);
            verify(adminRoleRelationMapper).deleteByExample(any(UmsAdminRoleRelationExample.class));
            verify(adminRoleRelationDao).insertList(roleRelationListCaptor.capture());
            List<UmsAdminRoleRelation> relations = roleRelationListCaptor.getValue();
            assertThat(relations).hasSize(2);
            assertThat(relations.get(0).getAdminId()).isEqualTo(1L);
            assertThat(relations.get(0).getRoleId()).isEqualTo(10L);
            assertThat(relations.get(1).getRoleId()).isEqualTo(20L);
            verify(cacheService).delResourceList(1L);
        }

        @Test
        @DisplayName("roleIds 为 null 时只删除不插入")
        void updateRole_nullRoleIds_onlyDeletes() {
            int count = adminService.updateRole(1L, null);

            assertThat(count).isEqualTo(0);
            verify(adminRoleRelationMapper).deleteByExample(any());
            verify(adminRoleRelationDao, never()).insertList(any());
        }
    }
    @Nested
    @DisplayName("updatePassword - 修改密码")
    class UpdatePasswordTests {
        @Test
        @DisplayName("参数为空时返回 -1")
        void updatePassword_emptyParams_returnsNegative1() {
            var param = new UpdateAdminPasswordParam("", "old", "new");
            assertThat(adminService.updatePassword(param)).isEqualTo(-1);
        }

        @Test
        @DisplayName("用户不存在时返回 -2")
        void updatePassword_userNotFound_returnsNegative2() {
            var param = new UpdateAdminPasswordParam("ghost", "old", "new");
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(Collections.emptyList());

            assertThat(adminService.updatePassword(param)).isEqualTo(-2);
        }

        @Test
        @DisplayName("旧密码不匹配时返回 -3")
        void updatePassword_wrongOldPassword_returnsNegative3() {
            var param = new UpdateAdminPasswordParam("admin", "wrong", "new");
            UmsAdmin admin = createAdmin(1L, "admin", "$encoded$");
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(List.of(admin));
            when(passwordEncoder.matches("wrong", "$encoded$")).thenReturn(false);

            assertThat(adminService.updatePassword(param)).isEqualTo(-3);
        }

        @Test
        @DisplayName("密码修改成功返回 1 并清除缓存")
        void updatePassword_success_returnsOne() {
            var param = new UpdateAdminPasswordParam("admin", "oldpass", "newpass");
            UmsAdmin admin = createAdmin(1L, "admin", "$old$");
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(List.of(admin));
            when(passwordEncoder.matches("oldpass", "$old$")).thenReturn(true);
            when(passwordEncoder.encode("newpass")).thenReturn("$new$");

            int result = adminService.updatePassword(param);

            assertThat(result).isEqualTo(1);
            verify(adminMapper).updateByPrimaryKey(adminCaptor.capture());
            assertThat(adminCaptor.getValue().getPassword()).isEqualTo("$new$");
            verify(cacheService).delAdmin(1L);
        }
    }

    @Nested
    @DisplayName("getResourceList - 获取用户资源列表")
    class GetResourceListTests {
        @Test
        @DisplayName("缓存命中时直接返回")
        void getResourceList_cacheHit_returnsCached() {
            var resources = List.of(new UmsResource());
            when(cacheService.getResourceList(1L)).thenReturn(resources);

            assertThat(adminService.getResourceList(1L)).isSameAs(resources);
            verifyNoInteractions(adminRoleRelationDao);
        }

        @Test
        @DisplayName("缓存未命中时查数据库并写入缓存")
        void getResourceList_cacheMiss_queriesAndCaches() {
            var resources = List.of(new UmsResource());
            when(cacheService.getResourceList(1L)).thenReturn(null);
            when(adminRoleRelationDao.getResourceList(1L)).thenReturn(resources);

            List<UmsResource> result = adminService.getResourceList(1L);

            assertThat(result).isEqualTo(resources);
            verify(cacheService).setResourceList(1L, resources);
        }
    }

    @Nested
    @DisplayName("loadUserByUsername - 加载 UserDetails")
    class LoadUserByUsernameTests {
        @Test
        @DisplayName("用户存在时返回 AdminUserDetails")
        void loadUserByUsername_exists_returnsUserDetails() {
            UmsAdmin admin = createAdmin(1L, "admin", "$encoded$");
            when(cacheService.getAdmin("admin")).thenReturn(admin);
            var resources = List.of(new UmsResource());
            when(cacheService.getResourceList(1L)).thenReturn(resources);

            var userDetails = adminService.loadUserByUsername("admin");

            assertThat(userDetails.getUsername()).isEqualTo("admin");
            assertThat(userDetails.getPassword()).isEqualTo("$encoded$");
        }

        @Test
        @DisplayName("用户不存在时抛出 UsernameNotFoundException")
        void loadUserByUsername_notFound_throws() {
            when(cacheService.getAdmin("ghost")).thenReturn(null);
            when(adminMapper.selectByExample(any(UmsAdminExample.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> adminService.loadUserByUsername("ghost"))
                    .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("refreshToken - 刷新 Token")
    class RefreshTokenTests {
        @Test
        @DisplayName("委托 jwtTokenUtil.refreshHeadToken")
        void refreshToken_delegates() {
            when(jwtTokenUtil.refreshHeadToken("old-token")).thenReturn("new-token");

            assertThat(adminService.refreshToken("old-token")).isEqualTo("new-token");
        }
    }
}
