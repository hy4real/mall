package com.macro.mall.searchmodern.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.macro.mall.searchmodern.dao.EsProductDao;
import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.repository.EsProductRepository;
import com.macro.mall.searchmodern.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EsProductServiceImpl 单元测试")
class EsProductServiceImplTest {

    @Mock
    private EsProductDao productDao;
    @Mock
    private EsProductRepository productRepository;
    @Mock
    private ElasticsearchClient esClient;
    @Mock
    private EmbeddingService embeddingService;

    private EsProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EsProductServiceImpl(productDao, productRepository, esClient, embeddingService);
    }

    @Test
    @DisplayName("importAll 空列表返回 0")
    void importAll_emptyList_returnsZero() {
        when(productDao.getAllEsProductList(null)).thenReturn(List.of());

        int count = service.importAll();

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("importAll 生成 embedding 并保存所有商品")
    void importAll_withProducts_savesAll() {
        EsProduct p1 = product(1L, "手机");
        EsProduct p2 = product(2L, "耳机");
        when(productDao.getAllEsProductList(null)).thenReturn(List.of(p1, p2));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[4], new float[4]));
        when(productRepository.saveAll(any())).thenReturn(List.of(p1, p2));

        int count = service.importAll();

        assertThat(count).isEqualTo(2);
        verify(embeddingService).embedBatch(any());
        verify(productRepository).saveAll(any());
    }

    @Test
    @DisplayName("delete 按 ID 删除单个商品")
    void delete_singleId_callsRepository() {
        service.delete(42L);
        verify(productRepository).deleteById(42L);
    }

    @Test
    @DisplayName("delete 批量删除空列表不调用 repository")
    void delete_emptyList_noOp() {
        service.delete(List.of());
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("create 查到商品后生成 embedding 并保存")
    void create_found_savesWithEmbedding() {
        EsProduct p = product(1L, "手机");
        when(productDao.getAllEsProductList(1L)).thenReturn(List.of(p));
        when(embeddingService.embed(any())).thenReturn(new float[4]);
        when(productRepository.save(any())).thenReturn(p);

        EsProduct result = service.create(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(embeddingService).embed(any());
        verify(productRepository).save(any());
    }

    @Test
    @DisplayName("create 查不到商品返回 null")
    void create_notFound_returnsNull() {
        when(productDao.getAllEsProductList(99L)).thenReturn(List.of());

        EsProduct result = service.create(99L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("search 按关键词分页查询")
    void search_byKeyword_returnsPage() {
        EsProduct p = product(1L, "智能手机");
        Page<EsProduct> expected = new org.springframework.data.domain.PageImpl<>(List.of(p), PageRequest.of(0, 10), 1);
        when(productRepository.findByNameOrSubTitleOrKeywords("手机", "手机", "手机", PageRequest.of(0, 10)))
                .thenReturn(expected);

        Page<EsProduct> page = service.search("手机", 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getName()).isEqualTo("智能手机");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("recommend 无商品返回空页")
    void recommend_noProduct_returnsEmptyPage() {
        when(productDao.getAllEsProductList(1L)).thenReturn(List.of());

        Page<EsProduct> page = service.recommend(1L, 0, 10);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // --- helpers ---

    private static EsProduct product(Long id, String name) {
        EsProduct p = new EsProduct();
        p.setId(id);
        p.setName(name);
        return p;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> SearchResponse<T> searchResponse(T source, long total) {
        Hit hit = Hit.of(h -> h.index("pms").id("1").source(source));
        HitsMetadata hitsMeta = HitsMetadata.of(h -> h
                .total(new TotalHits.Builder().value(total).relation(TotalHitsRelation.Eq).build())
                .hits(hit));
        return (SearchResponse) SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).successful(1).failed(0))
                .hits(hitsMeta));
    }
}
