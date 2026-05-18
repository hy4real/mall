package com.macro.mall.searchpg.service.impl;

import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;
import com.macro.mall.searchpg.repository.ProductEmbeddingRepository;
import com.macro.mall.searchpg.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final int DIMENSIONS = 1536;
    private final ProductEmbeddingRepository repository;

    @Override
    public void initDatabase() {
        repository.initSchema();
        log.info("Database schema initialized");
    }

    @Override
    public void indexProduct(ProductEmbedding product) {
        String text = product.getName() + " " + product.getDescription();
        product.setEmbedding(textToVector(text));
        repository.insert(product);
        log.info("Indexed product: {}", product.getProductId());
    }

    @Override
    public void batchIndexProducts(List<ProductEmbedding> products) {
        products.forEach(p -> {
            String text = p.getName() + " " + p.getDescription();
            p.setEmbedding(textToVector(text));
        });
        repository.batchInsert(products);
        log.info("Batch indexed {} products", products.size());
    }

    @Override
    public List<SimilarityResult> semanticSearch(String query, int limit) {
        double[] queryVec = textToVector(query);
        return repository.semanticSearch(queryVec, limit);
    }

    @Override
    public List<ProductEmbedding> getAllProducts() {
        return repository.findAll();
    }

    @Override
    public int getProductCount() {
        return repository.count();
    }

    /**
     * Deterministic text-to-vector using SHA-256 sliding windows.
     * Texts that share common words will produce vectors with similar regions,
     * so cosine similarity is non-zero for related texts.
     */
    private double[] textToVector(String text) {
        double[] vec = new double[DIMENSIONS];
        String lower = text.toLowerCase();
        String[] words = lower.split("[\\s,;.!?/()\\[\\]{}]+");

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String word : words) {
                if (word.isEmpty()) continue;
                byte[] hash = md.digest(word.getBytes(StandardCharsets.UTF_8));
                // Each SHA-256 hash = 32 bytes → use to set ~32 dimensions
                for (int j = 0; j < 32; j++) {
                    int idx = Math.abs(hash[j] % DIMENSIONS);
                    vec[idx] += 1.0;
                }
            }
            // Normalize to unit length
            double norm = 0;
            for (double v : vec) norm += v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < DIMENSIONS; i++) vec[i] /= norm;
            }
        } catch (Exception e) {
            log.error("Failed to generate vector", e);
        }
        return vec;
    }
}