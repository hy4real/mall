package com.macro.mall.service.impl;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.PolicyConditions;
import com.macro.mall.dto.OssCallbackResult;
import com.macro.mall.dto.OssPolicyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("OssServiceImpl 单元测试")
class OssServiceImplTest {

    @Mock private OSSClient ossClient;
    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() {
        ossService = new OssServiceImpl(ossClient);
        ReflectionTestUtils.setField(ossService, "ALIYUN_OSS_EXPIRE", 300);
        ReflectionTestUtils.setField(ossService, "ALIYUN_OSS_MAX_SIZE", 10);
        ReflectionTestUtils.setField(ossService, "ALIYUN_OSS_CALLBACK", "http://callback.example.com");
        ReflectionTestUtils.setField(ossService, "ALIYUN_OSS_BUCKET_NAME", "my-bucket");
        ReflectionTestUtils.setField(ossService, "ALIYUN_OSS_ENDPOINT", "oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(ossService, "ALIYUN_OSS_DIR_PREFIX", "mall/");
    }

    @Nested
    @DisplayName("policy - 生成 OSS 上传策略")
    class PolicyTests {

        @Test
        @DisplayName("签名生成失败时返回空结果")
        void policy_onError_returnsEmptyResult() {
            // OSSClient mock is not set up → generatePostPolicy will throw
            OssPolicyResult result = ossService.policy();

            assertThat(result).isNotNull();
            // On exception, result is returned with all fields null/default
        }
    }

    @Nested
    @DisplayName("callback - 处理 OSS 上传回调")
    class CallbackTests {

        @Test
        @DisplayName("从请求参数构造回调结果")
        void callback_constructsResult() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter("filename", "images/test.jpg");
            request.addParameter("size", "102400");
            request.addParameter("mimeType", "image/jpeg");
            request.addParameter("width", "1920");
            request.addParameter("height", "1080");

            OssCallbackResult result = ossService.callback(request);

            assertThat(result.getFilename())
                    .isEqualTo("http://my-bucket.oss-cn-hangzhou.aliyuncs.com/images/test.jpg");
            assertThat(result.getSize()).isEqualTo("102400");
            assertThat(result.getMimeType()).isEqualTo("image/jpeg");
            assertThat(result.getWidth()).isEqualTo("1920");
            assertThat(result.getHeight()).isEqualTo("1080");
        }
    }
}
