package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberLevel;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.security.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UmsMemberServiceImpl 单元测试")
class UmsMemberServiceImplTest {

    private PasswordEncoder passwordEncoder;
    private JwtTokenUtil jwtTokenUtil;
    private UmsMemberMapper memberMapper;
    private UmsMemberLevelMapper memberLevelMapper;
    private UmsMemberCacheService memberCacheService;
    private UmsMemberServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenUtil = mock(JwtTokenUtil.class);
        memberMapper = mock(UmsMemberMapper.class);
        memberLevelMapper = mock(UmsMemberLevelMapper.class);
        memberCacheService = mock(UmsMemberCacheService.class);
        service = new UmsMemberServiceImpl(passwordEncoder, jwtTokenUtil, memberMapper, memberLevelMapper, memberCacheService);
        ReflectionTestUtils.setField(service, "REDIS_KEY_PREFIX_AUTH_CODE", "ums:authCode");
        ReflectionTestUtils.setField(service, "AUTH_CODE_EXPIRE_SECONDS", 120L);
    }

    @Nested
    @DisplayName("getByUsername 方法")
    class GetByUsername {

        @Test
        @DisplayName("缓存命中时直接返回")
        void getByUsername_cacheHit_returnsCached() {
            UmsMember member = new UmsMember();
            member.setUsername("test");
            when(memberCacheService.getMember("test")).thenReturn(member);

            UmsMember result = service.getByUsername("test");

            assertThat(result.getUsername()).isEqualTo("test");
            verify(memberMapper, never()).selectByExample(any());
        }

        @Test
        @DisplayName("缓存未命中时查数据库并写入缓存")
        void getByUsername_cacheMiss_queriesDb() {
            UmsMember member = new UmsMember();
            member.setUsername("test");
            when(memberCacheService.getMember("test")).thenReturn(null);
            when(memberMapper.selectByExample(any())).thenReturn(List.of(member));

            UmsMember result = service.getByUsername("test");

            assertThat(result).isNotNull();
            verify(memberCacheService).setMember(member);
        }

        @Test
        @DisplayName("用户不存在时返回 null")
        void getByUsername_notFound_returnsNull() {
            when(memberCacheService.getMember("unknown")).thenReturn(null);
            when(memberMapper.selectByExample(any())).thenReturn(List.of());

            UmsMember result = service.getByUsername("unknown");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("register 方法")
    class Register {

        @Test
        @DisplayName("验证码错误时抛异常")
        void register_wrongAuthCode_throws() {
            when(memberCacheService.getAuthCode("13800000000")).thenReturn("000000");

            assertThatThrownBy(() -> service.register("user1", "pass", "13800000000", "123456"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("用户已存在时抛异常")
        void register_userExists_throws() {
            when(memberCacheService.getAuthCode("13800000000")).thenReturn("123456");
            when(memberMapper.selectByExample(any())).thenReturn(List.of(new UmsMember()));

            assertThatThrownBy(() -> service.register("user1", "pass", "13800000000", "123456"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("正常注册创建会员")
        void register_valid_createsMember() {
            when(memberCacheService.getAuthCode("13800000000")).thenReturn("123456");
            when(memberMapper.selectByExample(any())).thenReturn(List.of());
            when(passwordEncoder.encode("pass")).thenReturn("encoded_pass");
            UmsMemberLevel level = new UmsMemberLevel();
            level.setId(1L);
            when(memberLevelMapper.selectByExample(any())).thenReturn(List.of(level));
            when(memberMapper.insert(any())).thenReturn(1);

            service.register("user1", "pass", "13800000000", "123456");

            verify(memberMapper).insert(argThat(m -> m.getUsername().equals("user1")
                    && m.getPassword() == null // 密码在 insert 后被清空
                    && m.getStatus() == 1));
        }
    }

    @Nested
    @DisplayName("generateAuthCode 方法")
    class GenerateAuthCode {

        @Test
        @DisplayName("生成 6 位验证码并存入缓存")
        void generateAuthCode_returns6DigitCode() {
            String code = service.generateAuthCode("13800000000");

            assertThat(code).hasSize(6);
            assertThat(code).matches("\\d{6}");
            verify(memberCacheService).setAuthCode(eq("13800000000"), eq(code));
        }
    }

    @Nested
    @DisplayName("updatePassword 方法")
    class UpdatePassword {

        @Test
        @DisplayName("账号不存在时抛异常")
        void updatePassword_accountNotFound_throws() {
            when(memberMapper.selectByExample(any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.updatePassword("13800000000", "newpass", "123456"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("验证码错误时抛异常")
        void updatePassword_wrongAuthCode_throws() {
            UmsMember member = new UmsMember();
            member.setId(1L);
            when(memberMapper.selectByExample(any())).thenReturn(List.of(member));
            when(memberCacheService.getAuthCode("13800000000")).thenReturn("000000");

            assertThatThrownBy(() -> service.updatePassword("13800000000", "newpass", "123456"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("正常更新密码")
        void updatePassword_valid_updatesPassword() {
            UmsMember member = new UmsMember();
            member.setId(1L);
            when(memberMapper.selectByExample(any())).thenReturn(List.of(member));
            when(memberCacheService.getAuthCode("13800000000")).thenReturn("123456");
            when(passwordEncoder.encode("newpass")).thenReturn("encoded");

            service.updatePassword("13800000000", "newpass", "123456");

            verify(memberMapper).updateByPrimaryKeySelective(any());
            verify(memberCacheService).delMember(1L);
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetById {

        @Test
        @DisplayName("根据 ID 查询会员")
        void getById_returnsMember() {
            UmsMember member = new UmsMember();
            member.setId(1L);
            when(memberMapper.selectByPrimaryKey(1L)).thenReturn(member);

            UmsMember result = service.getById(1L);

            assertThat(result.getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("updateIntegration 方法")
    class UpdateIntegration {

        @Test
        @DisplayName("更新积分后清除缓存")
        void updateIntegration_updatesAndClearsCache() {
            service.updateIntegration(1L, 500);

            verify(memberMapper).updateByPrimaryKeySelective(any());
            verify(memberCacheService).delMember(1L);
        }
    }

    @Nested
    @DisplayName("refreshToken 方法")
    class RefreshToken {

        @Test
        @DisplayName("刷新 JWT Token")
        void refreshToken_returnsNewToken() {
            when(jwtTokenUtil.refreshHeadToken("old-token")).thenReturn("new-token");

            String result = service.refreshToken("old-token");

            assertThat(result).isEqualTo("new-token");
        }
    }
}
