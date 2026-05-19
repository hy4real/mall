package com.macro.mall.searchpg.service.impl;

import com.macro.mall.searchpg.domain.HybridSearchResult;
import com.macro.mall.searchpg.repository.ProductEmbeddingRepository;
import com.macro.mall.searchpg.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VectorSearchServiceImpl tests")
class VectorSearchServiceImplTest {

    private RecordingProductEmbeddingRepository repository;
    private RecordingEmbeddingService embeddingService;
    private VectorSearchServiceImpl vectorSearchService;

    @BeforeEach
    void setUp() {
        repository = new RecordingProductEmbeddingRepository();
        embeddingService = new RecordingEmbeddingService();
        vectorSearchService = new VectorSearchServiceImpl(repository, embeddingService, null);
    }

    @Nested
    @DisplayName("hybridSearch")
    class HybridSearchTests {

        @Test
        @DisplayName("returns empty result for blank query")
        void hybridSearch_blankQuery_returnsEmptyResult() {
            List<HybridSearchResult> result = vectorSearchService.hybridSearch("  ", 10, 0.7d, 0.3d);

            assertThat(result).isEmpty();
            assertThat(embeddingService.requestedText).isNull();
            assertThat(repository.calls).isZero();
        }

        @Test
        @DisplayName("embeds query and normalizes weights")
        void hybridSearch_normalizesWeights() {
            double[] queryVector = new double[]{0.1d, 0.2d};
            List<HybridSearchResult> expected = List.of(HybridSearchResult.builder()
                    .productId(1L)
                    .name("小米电视")
                    .similarity(0.8d)
                    .textScore(1.0d)
                    .hybridScore(0.85d)
                    .build());

            embeddingService.vector = queryVector;
            repository.result = expected;

            List<HybridSearchResult> result = vectorSearchService.hybridSearch("小米电视", 5, 3.0d, 1.0d);

            assertThat(result).isSameAs(expected);
            assertThat(embeddingService.requestedText).isEqualTo("小米电视");
            assertThat(repository.queryEmbedding).isSameAs(queryVector);
            assertThat(repository.keyword).isEqualTo("小米电视");
            assertThat(repository.limit).isEqualTo(5);
            assertThat(repository.vectorWeight).isEqualTo(0.75d);
            assertThat(repository.textWeight).isEqualTo(0.25d);
        }

        @Test
        @DisplayName("uses default weights and minimum limit for invalid input")
        void hybridSearch_invalidWeightsAndLimit_usesDefaults() {
            double[] queryVector = new double[]{0.3d, 0.4d};
            embeddingService.vector = queryVector;
            repository.result = List.of();

            List<HybridSearchResult> result = vectorSearchService.hybridSearch("耳机", 0, 0.0d, -1.0d);

            assertThat(result).isEmpty();
            assertThat(repository.queryEmbedding).isSameAs(queryVector);
            assertThat(repository.keyword).isEqualTo("耳机");
            assertThat(repository.limit).isEqualTo(1);
            assertThat(repository.vectorWeight).isEqualTo(0.7d);
            assertThat(repository.textWeight).isEqualTo(0.3d);
        }
    }

    private static class RecordingEmbeddingService implements EmbeddingService {
        private double[] vector = new double[]{0.1d};
        private String requestedText;

        @Override
        public double[] embed(String text) {
            requestedText = text;
            return vector;
        }

        @Override
        public List<double[]> embedBatch(List<String> texts) {
            throw new UnsupportedOperationException("Not needed in hybrid search tests");
        }

        @Override
        public int dims() {
            return vector.length;
        }
    }

    private static class RecordingProductEmbeddingRepository extends ProductEmbeddingRepository {
        private List<HybridSearchResult> result = List.of();
        private int calls;
        private double[] queryEmbedding;
        private String keyword;
        private int limit;
        private double vectorWeight;
        private double textWeight;

        @Override
        public List<HybridSearchResult> hybridSearch(double[] queryEmbedding, String keyword, int limit,
                                                     double vectorWeight, double textWeight) {
            calls++;
            this.queryEmbedding = queryEmbedding;
            this.keyword = keyword;
            this.limit = limit;
            this.vectorWeight = vectorWeight;
            this.textWeight = textWeight;
            return result;
        }
    }
}
