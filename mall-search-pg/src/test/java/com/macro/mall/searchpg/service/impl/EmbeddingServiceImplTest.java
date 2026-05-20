package com.macro.mall.searchpg.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("EmbeddingServiceImpl 单元测试")
class EmbeddingServiceImplTest {

    private MockRestServiceServer server;
    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        embeddingService = new EmbeddingServiceImpl(
                "http://embedding.test",
                "text-embedding-3-small",
                "",
                4,
                5000,
                restTemplate);
    }

    @Test
    @DisplayName("embed 返回 API 解析后的向量")
    void embed_success_returnsVector() {
        server.expect(requestTo("http://embedding.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.1,0.2,0.3,0.4]}]}
                        """, MediaType.APPLICATION_JSON));

        double[] result = embeddingService.embed("hello");

        assertThat(result).hasSize(4);
        assertThat(result[0]).isEqualTo(0.1, offset(0.001));
        assertThat(result[3]).isEqualTo(0.4, offset(0.001));
        server.verify();
    }

    @Test
    @DisplayName("embedBatch 返回与输入顺序对应的向量列表")
    void embedBatch_success_returnsInOrder() {
        server.expect(requestTo("http://embedding.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":[
                          {"embedding":[0.1,0.2,0.3,0.4]},
                          {"embedding":[0.5,0.6,0.7,0.8]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<double[]> result = embeddingService.embedBatch(List.of("a", "b"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)[0]).isEqualTo(0.1, offset(0.001));
        assertThat(result.get(1)[0]).isEqualTo(0.5, offset(0.001));
        server.verify();
    }

    @Test
    @DisplayName("API 返回错误时返回零向量")
    void embed_apiError_returnsZeroVector() {
        server.expect(requestTo("http://embedding.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        double[] result = embeddingService.embed("hello");

        assertThat(result).hasSize(4);
        assertThat(result).containsOnly(0.0);
        server.verify();
    }

    @Test
    @DisplayName("embedBatch API 异常时每个元素返回零向量")
    void embedBatch_apiError_returnsZeroVectors() {
        server.expect(requestTo("http://embedding.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        List<double[]> result = embeddingService.embedBatch(List.of("a", "b", "c"));

        assertThat(result).hasSize(3);
        for (double[] vec : result) {
            assertThat(vec).hasSize(4);
            assertThat(vec).containsOnly(0.0);
        }
        server.verify();
    }

    @Test
    @DisplayName("配置了 API key 时请求头携带 Bearer token")
    void embed_withApiKey_sendsBearerHeader() {
        RestTemplate restTemplateWithKey = new RestTemplate();
        MockRestServiceServer serverWithKey = MockRestServiceServer.bindTo(restTemplateWithKey).build();
        EmbeddingServiceImpl serviceWithKey = new EmbeddingServiceImpl(
                "http://embedding.test",
                "text-embedding-3-small",
                "sk-test-key",
                4,
                5000,
                restTemplateWithKey);

        serverWithKey.expect(requestTo("http://embedding.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-key"))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.1,0.2,0.3,0.4]}]}
                        """, MediaType.APPLICATION_JSON));

        double[] result = serviceWithKey.embed("hello");

        assertThat(result).hasSize(4);
        serverWithKey.verify();
    }

    @Test
    @DisplayName("dims 返回配置的向量维度")
    void dims_returnsConfiguredDimension() {
        assertThat(embeddingService.dims()).isEqualTo(4);
    }
}
