package com.macro.mall.service.impl;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.mapper.UmsAdminRoleRelationMapper;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsAdminRoleRelation;
import com.macro.mall.model.UmsAdminRoleRelationExample;
import com.macro.mall.model.UmsResource;
import com.macro.mall.service.UmsAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsAdminCacheServiceImpl 单元测试")
class UmsAdminCacheServiceImplTest {

    @Mock private UmsAdminService adminService;
    @Mock private RedisService redisService;
    @Mock private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Mock private UmsAdminRoleRelationDao adminRoleRelationDao;

    private UmsAdminCacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new UmsAdminCacheServiceImpl(
                adminService, redisService, adminRoleRelationMapper, adminRoleRelationDao);
        ReflectionTestUtils.setField(cacheService, "REDIS_DATABASE", "mall");
        ReflectionTestUtils.setField(cacheService, "REDIS_EXPIRE", 86400L);
        ReflectionTestUtils.setField(cacheService, "REDIS_KEY_ADMIN", "ums:admin");
        ReflectionTestUtils.setField(cacheService, "REDIS_KEY_RESOURCE_LIST", "ums:resourceList");
    }

    @Nested
    @DisplayName("delAdmin - 删除后台用户缓存")
    class DelAdminTests {

        @Test
        @DisplayName("用户存在时删除 Redis 缓存")
        void delAdmin_exists_deletesCache() {
            var admin = new UmsAdmin();
            admin.setUsername("admin");
            when(adminService.getItem(1L)).thenReturn(admin);

            cacheService.delAdmin(1L);

            verify(redisService).del("mall:ums:admin:admin");
        }

        @Test
        @DisplayName("用户不存在时跳过")
        void delAdmin_notFound_skips() {
            when(adminService.getItem(99L)).thenReturn(null);

            cacheService.delAdmin(99L);

            verify(redisService, never()).del(anyString());
        }
    }

    @Nested
    @DisplayName("delResourceList - 删除用户资源列表缓存")
    class DelResourceListTests {

        @Test
        @DisplayName("删除指定 adminId 的资源缓存")
        void delResourceList_deletesKey() {
            cacheService.delResourceList(5L);

            verify(redisService).del("mall:ums:resourceList:5");
        }
    }

    @Nested
    @DisplayName("delResourceListByRole - 根据角色删除资源列表缓存")
    class DelResourceListByRoleTests {

        @Test
        @DisplayName("批量删除角色关联用户的资源缓存")
        void delResourceListByRole_batchDeletes() {
            var rel1 = new UmsAdminRoleRelation();
            rel1.setAdminId(1L);
            var rel2 = new UmsAdminRoleRelation();
            rel2.setAdminId(2L);
            when(adminRoleRelationMapper.selectByExample(any(UmsAdminRoleRelationExample.class)))
                    .thenReturn(List.of(rel1, rel2));

            cacheService.delResourceListByRole(10L);

            verify(redisService).del(List.of("mall:ums:resourceList:1", "mall:ums:resourceList:2"));
        }

        @Test
        @DisplayName("无关联用户时跳过")
        void delResourceListByRole_empty_skips() {
            when(adminRoleRelationMapper.selectByExample(any(UmsAdminRoleRelationExample.class)))
                    .thenReturn(List.of());

            cacheService.delResourceListByRole(10L);

            verify(redisService, never()).del(anyList());
        }
    }

    @Nested
    @DisplayName("delResourceListByRoleIds - 根据多个角色删除缓存")
    class DelResourceListByRoleIdsTests {

        @Test
        @DisplayName("批量删除多个角色的用户资源缓存")
        void delResourceListByRoleIds_batchDeletes() {
            var rel = new UmsAdminRoleRelation();
            rel.setAdminId(3L);
            when(adminRoleRelationMapper.selectByExample(any(UmsAdminRoleRelationExample.class)))
                    .thenReturn(List.of(rel));

            cacheService.delResourceListByRoleIds(List.of(10L, 20L));

            verify(redisService).del(List.of("mall:ums:resourceList:3"));
        }
    }

    @Nested
    @DisplayName("delResourceListByResource - 根据资源删除缓存")
    class DelResourceListByResourceTests {

        @Test
        @DisplayName("删除资源关联的所有用户资源缓存")
        void delResourceListByResource_batchDeletes() {
            when(adminRoleRelationDao.getAdminIdList(5L)).thenReturn(List.of(1L, 2L, 3L));

            cacheService.delResourceListByResource(5L);

            verify(redisService).del(List.of(
                    "mall:ums:resourceList:1",
                    "mall:ums:resourceList:2",
                    "mall:ums:resourceList:3"));
        }

        @Test
        @DisplayName("无关联用户时跳过")
        void delResourceListByResource_empty_skips() {
            when(adminRoleRelationDao.getAdminIdList(5L)).thenReturn(List.of());

            cacheService.delResourceListByResource(5L);

            verify(redisService, never()).del(anyList());
        }
    }

    @Nested
    @DisplayName("getAdmin / setAdmin - 用户缓存读写")
    class AdminCacheTests {

        @Test
        @DisplayName("从 Redis 获取缓存用户")
        void getAdmin_getsFromRedis() {
            var admin = new UmsAdmin();
            when(redisService.get("mall:ums:admin:admin")).thenReturn(admin);

            assertThat(cacheService.getAdmin("admin")).isSameAs(admin);
        }

        @Test
        @DisplayName("将用户写入 Redis 缓存")
        void setAdmin_setsToRedis() {
            var admin = new UmsAdmin();
            admin.setUsername("admin");

            cacheService.setAdmin(admin);

            verify(redisService).set(eq("mall:ums:admin:admin"), eq(admin), anyLong());
        }
    }

    @Nested
    @DisplayName("getResourceList / setResourceList - 资源列表缓存")
    class ResourceListCacheTests {

        @Test
        @DisplayName("从 Redis 获取资源列表")
        void getResourceList_getsFromRedis() {
            var resources = List.of(new UmsResource());
            when(redisService.get("mall:ums:resourceList:10")).thenReturn(resources);

            assertThat(cacheService.getResourceList(10L)).isSameAs(resources);
        }

        @Test
        @DisplayName("将资源列表写入 Redis")
        void setResourceList_setsToRedis() {
            var resources = List.of(new UmsResource());

            cacheService.setResourceList(10L, resources);

            verify(redisService).set(eq("mall:ums:resourceList:10"), eq(resources), anyLong());
        }
    }
}
