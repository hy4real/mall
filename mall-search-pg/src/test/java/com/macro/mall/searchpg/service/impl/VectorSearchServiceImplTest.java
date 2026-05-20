package com.macro.mall.searchpg.service.impl;

import com.macro.mall.searchpg.domain.HybridSearchResult;
import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;
import com.macro.mall.searchpg.reader.MysqlProductReader;
import com.macro.mall.searchpg.reader.MysqlProductReader.ProductRow;
import com.macro.mall.searchpg.repository.ProductEmbeddingRepository;
import com.macro.mall.searchpg.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VectorSearchServiceImpl tests")
class VectorSearchServiceImplTest {

    private RecordingProductEmbeddingRepository repository;
    private RecordingEmbeddingService embeddingService;
    private RecordingMysqlProductReader mysqlReader;
    private VectorSearchServiceImpl vectorSearchService;

    @BeforeEach
    void setUp() {
        repository = new RecordingProductEmbeddingRepository();
        embeddingService = new RecordingEmbeddingService();
        mysqlReader = new RecordingMysqlProductReader();
        vectorSearchService = new VectorSearchServiceImpl(repository, embeddingService, mysqlReader);
    }

    @Nested
    @DisplayName("initDatabase")
    class InitDatabaseTests {

        @Test
        @DisplayName("calls initSchema with embedding dims")
        void initDatabase_callsInitSchemaWithDims() {
            embeddingService.dims = 4;

            vectorSearchService.initDatabase();

            assertThat(repository.initSchemaDims).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("indexProduct")
    class IndexProductTests {

        @Test
        @DisplayName("embeds product text and inserts")
        void indexProduct_embedsAndInserts() {
            embeddingService.vector = new double[]{0.1, 0.2, 0.3};
            ProductEmbedding product = ProductEmbedding.builder()
                    .productId(1L).name("手机").description("智能手机").build();

            vectorSearchService.indexProduct(product);

            assertThat(embeddingService.requestedText).isEqualTo("手机 智能手机");
            assertThat(repository.insertedProducts).hasSize(1);
            assertThat(repository.insertedProducts.getFirst().getProductId()).isEqualTo(1L);
            assertThat(repository.insertedProducts.getFirst().getEmbedding()).isEqualTo(embeddingService.vector);
        }
    }

    @Nested
    @DisplayName("batchIndexProducts")
    class BatchIndexProductsTests {

        @Test
        @DisplayName("embeds all products and batch inserts")
        void batchIndexProducts_embedsAndBatchInserts() {
            embeddingService.batchVectors = List.of(
                    new double[]{0.1, 0.2},
                    new double[]{0.3, 0.4});
            List<ProductEmbedding> products = List.of(
                    ProductEmbedding.builder().productId(1L).name("手机").build(),
                    ProductEmbedding.builder().productId(2L).name("耳机").build());

            vectorSearchService.batchIndexProducts(products);

            assertThat(embeddingService.requestedTexts).containsExactly("手机", "耳机");
            assertThat(repository.batchInsertedProducts).hasSize(2);
            assertThat(repository.batchInsertedProducts.get(0).getEmbedding()).isEqualTo(new double[]{0.1, 0.2});
            assertThat(repository.batchInsertedProducts.get(1).getEmbedding()).isEqualTo(new double[]{0.3, 0.4});
        }

        @Test
        @DisplayName("empty list does not call repository")
        void batchIndexProducts_emptyList_noOp() {
            vectorSearchService.batchIndexProducts(List.of());

            assertThat(repository.batchInsertedProducts).isNull();
        }
    }

    @Nested
    @DisplayName("semanticSearch")
    class SemanticSearchTests {

        @Test
        @DisplayName("embeds query and delegates to repository")
        void semanticSearch_embedsAndDelegates() {
            embeddingService.vector = new double[]{0.5, 0.6};
            List<SimilarityResult> expected = List.of(SimilarityResult.builder()
                    .productId(1L).name("手机").similarity(0.9).build());
            repository.semanticResults = expected;

            List<SimilarityResult> result = vectorSearchService.semanticSearch("手机", 10);

            assertThat(result).isSameAs(expected);
            assertThat(embeddingService.requestedText).isEqualTo("手机");
            assertThat(repository.semanticQueryEmbedding).isSameAs(embeddingService.vector);
            assertThat(repository.semanticLimit).isEqualTo(10);
        }
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

    @Nested
    @DisplayName("getAllProducts / getProductCount")
    class QueryTests {

        @Test
        @DisplayName("getAllProducts delegates to repository")
        void getAllProducts_delegates() {
            List<ProductEmbedding> expected = List.of(
                    ProductEmbedding.builder().productId(1L).name("手机").build());
            repository.findAllResult = expected;

            assertThat(vectorSearchService.getAllProducts()).isSameAs(expected);
        }

        @Test
        @DisplayName("getProductCount delegates to repository")
        void getProductCount_delegates() {
            repository.countResult = 42;

            assertThat(vectorSearchService.getProductCount()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("syncFromMysql")
    class SyncFromMysqlTests {

        @Test
        @DisplayName("reads from MySQL and batch indexes with embeddings")
        void syncFromMysql_readsAndBatchIndexes() {
            mysqlReader.rows = List.of(
                    new ProductRow(1L, "手机", "智能旗舰", "5G", "华为", "手机"),
                    new ProductRow(2L, "耳机", "降噪耳机", "蓝牙", "索尼", "耳机"));
            embeddingService.batchVectors = List.of(
                    new double[]{0.1, 0.2},
                    new double[]{0.3, 0.4});

            int count = vectorSearchService.syncFromMysql();

            assertThat(count).isEqualTo(2);
            assertThat(embeddingService.requestedTexts).containsExactly(
                    "手机 智能旗舰 5G", "耳机 降噪耳机 蓝牙");
            assertThat(repository.batchInsertedProducts).hasSize(2);
            assertThat(repository.batchInsertedProducts.get(0).getProductId()).isEqualTo(1L);
            assertThat(repository.batchInsertedProducts.get(0).getCategory()).isEqualTo("手机");
            assertThat(repository.batchInsertedProducts.get(0).getBrand()).isEqualTo("华为");
        }
    }

    // --- test doubles ---

    private static class RecordingEmbeddingService implements EmbeddingService {
        private double[] vector = new double[]{0.1d};
        private List<double[]> batchVectors = List.of();
        private int dims = 2;
        private String requestedText;
        private List<String> requestedTexts;

        @Override
        public double[] embed(String text) {
            requestedText = text;
            return vector;
        }

        @Override
        public List<double[]> embedBatch(List<String> texts) {
            requestedTexts = new ArrayList<>(texts);
            return batchVectors;
        }

        @Override
        public int dims() {
            return dims;
        }
    }

    private static class RecordingProductEmbeddingRepository extends ProductEmbeddingRepository {
        // hybridSearch
        private List<HybridSearchResult> result = List.of();
        private int calls;
        private double[] queryEmbedding;
        private String keyword;
        private int limit;
        private double vectorWeight;
        private double textWeight;

        // initSchema
        private Integer initSchemaDims;

        // insert / batchInsert
        private List<ProductEmbedding> insertedProducts = new ArrayList<>();
        private List<ProductEmbedding> batchInsertedProducts;

        // semanticSearch
        private List<SimilarityResult> semanticResults = List.of();
        private double[] semanticQueryEmbedding;
        private int semanticLimit;

        // findAll / count
        private List<ProductEmbedding> findAllResult = List.of();
        private int countResult;

        @Override
        public void initSchema(int dims) {
            this.initSchemaDims = dims;
        }

        @Override
        public void insert(ProductEmbedding embedding) {
            insertedProducts.add(embedding);
        }

        @Override
        public void batchInsert(List<ProductEmbedding> embeddings) {
            batchInsertedProducts = new ArrayList<>(embeddings);
        }

        @Override
        public List<SimilarityResult> semanticSearch(double[] queryEmbedding, int limit) {
            this.semanticQueryEmbedding = queryEmbedding;
            this.semanticLimit = limit;
            return semanticResults;
        }

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

        @Override
        public List<ProductEmbedding> findAll() {
            return findAllResult;
        }

        @Override
        public int count() {
            return countResult;
        }
    }

    private static class RecordingMysqlProductReader extends MysqlProductReader {
        private List<ProductRow> rows = List.of();

        RecordingMysqlProductReader() {
            super(org.mockito.Mockito.mock(javax.sql.DataSource.class));
        }

        @Override
        public List<ProductRow> readAllProducts() {
            return rows;
        }
    }
}
