package com.macro.mall.searchpg.repository;

import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductEmbeddingRepository {

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

    public void initSchema() {
        jdbcTemplate.execute("""
            CREATE EXTENSION IF NOT EXISTS vector;

            CREATE TABLE IF NOT EXISTS product_embeddings (
                id BIGSERIAL PRIMARY KEY,
                product_id BIGINT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                embedding vector(1536) NOT NULL,
                category TEXT,
                brand TEXT,
                created_at TIMESTAMP DEFAULT NOW()
            );

            CREATE INDEX IF NOT EXISTS idx_product_embeddings_embedding
            ON product_embeddings USING hnsw (embedding vector_cosine_ops);

            CREATE UNIQUE INDEX IF NOT EXISTS idx_product_embeddings_product_id
            ON product_embeddings (product_id);
        """);
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