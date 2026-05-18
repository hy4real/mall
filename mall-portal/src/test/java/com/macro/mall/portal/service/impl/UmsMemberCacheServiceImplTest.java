package com.macro.mall.portal.service.impl;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsMemberCacheServiceImpl 单元测试")
class UmsMemberCacheServiceImplTest {

    private RedisService redisService;
    private UmsMemberMapper memberMapper;
    private UmsMemberCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        redisService = mock(RedisService.class);
        memberMapper = mock(UmsMemberMapper.class);
        service = new UmsMemberCacheServiceImpl(redisService, memberMapper);
        ReflectionTestUtils.setField(service, "REDIS_DATABASE", "mall");
        ReflectionTestUtils.setField(service, "REDIS_EXPIRE", 3600L);
        ReflectionTestUtils.setField(service, "REDIS_EXPIRE_AUTH_CODE", 120L);
        ReflectionTestUtils.setField(service, "REDIS_KEY_MEMBER", "ums:member");
        ReflectionTestUtils.setField(service, "REDIS_KEY_AUTH_CODE", "ums:authCode");
    }

    @Nested
    @DisplayName("getMember 方法")
    class GetMember {

        @Test
        @DisplayName("从 Redis 获取会员缓存")
        void getMember_cached_returnsFromRedis() {
            UmsMember member = new UmsMember();
            member.setUsername("test");
            when(redisService.get("mall:ums:member:test")).thenReturn(member);

            UmsMember result = service.getMember("test");

            assertThat(result.getUsername()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("setMember 方法")
    class SetMember {

        @Test
        @DisplayName("缓存会员到 Redis 并设置过期时间")
        void setMember_storesWithExpiry() {
            UmsMember member = new UmsMember();
            member.setUsername("test");

            service.setMember(member);

            verify(redisService).set("mall:ums:member:test", member, 3600L);
        }
    }

    @Nested
    @DisplayName("delMember 方法")
    class DelMember {

        @Test
        @DisplayName("会员存在时删除缓存")
        void delMember_exists_deletesKey() {
            UmsMember member = new UmsMember();
            member.setUsername("test");
            when(memberMapper.selectByPrimaryKey(1L)).thenReturn(member);

            service.delMember(1L);

            verify(redisService).del("mall:ums:member:test");
        }

        @Test
        @DisplayName("会员不存在时不操作 Redis")
        void delMember_notExists_noop() {
            when(memberMapper.selectByPrimaryKey(99L)).thenReturn(null);

            service.delMember(99L);

            verify(redisService, never()).del(anyString());
        }
    }

    @Nested
    @DisplayName("setAuthCode / getAuthCode 方法")
    class AuthCode {

        @Test
        @DisplayName("存储验证码到 Redis")
        void setAuthCode_storesWithExpiry() {
            service.setAuthCode("13800000000", "123456");

            verify(redisService).set("mall:ums:authCode:13800000000", "123456", 120L);
        }

        @Test
        @DisplayName("从 Redis 获取验证码")
        void getAuthCode_returnsCachedCode() {
            when(redisService.get("mall:ums:authCode:13800000000")).thenReturn("654321");

            String result = service.getAuthCode("13800000000");

            assertThat(result).isEqualTo("654321");
        }
    }
}
