package com.macro.mall.searchpg.repository;

import com.macro.mall.searchpg.domain.HybridSearchResult;
import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductEmbeddingRepository {

    private static final String SEARCH_TEXT_SQL = """
            coalesce(name, '') || ' ' ||
            coalesce(description, '') || ' ' ||
            coalesce(category, '') || ' ' ||
            coalesce(brand, '')
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ProductEmbedding> productEmbeddingRowMapper = (rs, rowNum) -> ProductEmbedding.builder()
            .id(rs.getLong("id"))
            .productId(rs.getLong("product_id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .category(rs.getString("category"))
            .brand(rs.getString("brand"))
            .build();

    private final RowMapper<SimilarityResult> similarityResultRowMapper = (rs, rowNum) -> SimilarityResult.builder()
            .productId(rs.getLong("product_id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .similarity(rs.getDouble("similarity"))
            .build();

    private final RowMapper<HybridSearchResult> hybridSearchResultRowMapper = (rs, rowNum) -> HybridSearchResult.builder()
            .productId(rs.getLong("product_id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .category(rs.getString("category"))
            .brand(rs.getString("brand"))
            .similarity(rs.getDouble("similarity"))
            .textScore(rs.getDouble("text_score"))
            .hybridScore(rs.getDouble("hybrid_score"))
            .build();

    public void initSchema(int dims) {
        jdbcTemplate.execute(String.format("""
            CREATE EXTENSION IF NOT EXISTS vector;
            CREATE EXTENSION IF NOT EXISTS pg_trgm;

            CREATE TABLE IF NOT EXISTS product_embeddings (
                id BIGSERIAL PRIMARY KEY,
                product_id BIGINT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                embedding vector(%d) NOT NULL,
                category TEXT,
                brand TEXT,
                created_at TIMESTAMP DEFAULT NOW()
            );

            CREATE INDEX IF NOT EXISTS idx_product_embeddings_embedding
            ON product_embeddings USING hnsw (embedding vector_cosine_ops);

            CREATE UNIQUE INDEX IF NOT EXISTS idx_product_embeddings_product_id
            ON product_embeddings (product_id);

            CREATE INDEX IF NOT EXISTS idx_product_embeddings_text_trgm
            ON product_embeddings USING gin ((%s) gin_trgm_ops);
        """, dims, SEARCH_TEXT_SQL));
    }

    public void insert(ProductEmbedding embedding) {
        if (embedding.getEmbedding() == null || embedding.getEmbedding().length == 0) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }

        String arrayStr = arrayToPgVector(embedding.getEmbedding());

        jdbcTemplate.update("""
            INSERT INTO product_embeddings (product_id, name, description, embedding, category, brand)
            VALUES (?, ?, ?, ?::vector, ?, ?)
            ON CONFLICT (product_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                embedding = EXCLUDED.embedding,
                category = EXCLUDED.category,
                brand = EXCLUDED.brand
        """, embedding.getProductId(), embedding.getName(), embedding.getDescription(),
            arrayStr, embedding.getCategory(), embedding.getBrand());
    }

    public void batchInsert(List<ProductEmbedding> embeddings) {
        embeddings.forEach(this::insert);
    }

    public List<SimilarityResult> semanticSearch(double[] queryEmbedding, int limit) {
        String queryVector = arrayToPgVector(queryEmbedding);

        String sql = String.format("""
            SELECT product_id, name, description,
                   1 - (embedding <=> '%s'::vector) as similarity
            FROM product_embeddings
            ORDER BY embedding <=> '%s'::vector
            LIMIT %d
        """, queryVector, queryVector, limit);

        return jdbcTemplate.query(sql, similarityResultRowMapper);
    }

    public List<HybridSearchResult> hybridSearch(double[] queryEmbedding, String keyword, int limit,
                                                 double vectorWeight, double textWeight) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            throw new IllegalArgumentException("Query embedding cannot be null or empty");
        }

        String queryVector = arrayToPgVector(queryEmbedding);
        String searchKeyword = keyword == null ? "" : keyword.trim();

        String sql = String.format("""
            WITH scored AS (
                SELECT product_id,
                       name,
                       description,
                       category,
                       brand,
                       GREATEST(0.0, 1 - (embedding <=> ?::vector)) AS similarity,
                       GREATEST(
                           similarity(search_text, ?),
                           CASE WHEN search_text ILIKE ('%%' || ? || '%%') THEN 1.0 ELSE 0.0 END
                       ) AS text_score
                FROM (
                    SELECT product_id,
                           name,
                           description,
                           category,
                           brand,
                           embedding,
                           %s AS search_text
                    FROM product_embeddings
                ) product_text
            )
            SELECT product_id,
                   name,
                   description,
                   category,
                   brand,
                   similarity,
                   text_score,
                   (? * similarity + ? * text_score) AS hybrid_score
            FROM scored
            ORDER BY hybrid_score DESC, similarity DESC, product_id
            LIMIT ?
        """, SEARCH_TEXT_SQL);

        return jdbcTemplate.query(sql, hybridSearchResultRowMapper,
                queryVector, searchKeyword, searchKeyword, vectorWeight, textWeight, limit);
    }

    public ProductEmbedding findByProductId(Long productId) {
        return jdbcTemplate.queryForObject("""
            SELECT id, product_id, name, description, category, brand
            FROM product_embeddings
            WHERE product_id = ?
        """, productEmbeddingRowMapper, productId);
    }

    public List<ProductEmbedding> findAll() {
        return jdbcTemplate.query("""
            SELECT id, product_id, name, description, category, brand
            FROM product_embeddings
            ORDER BY id
        """, productEmbeddingRowMapper);
    }

    public int deleteByProductId(Long productId) {
        return jdbcTemplate.update("DELETE FROM product_embeddings WHERE product_id = ?", productId);
    }

    public int count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_embeddings", Integer.class);
    }

    private String arrayToPgVector(double[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
