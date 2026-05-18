package com.macro.mall.searchpg.service;

import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;

import java.util.List;

public interface VectorSearchService {
    void initDatabase();

    void indexProduct(ProductEmbedding product);

    void batchIndexProducts(List<ProductEmbedding> products);

    List<SimilarityResult> semanticSearch(String query, int limit);

    List<ProductEmbedding> getAllProducts();

    int getProductCount();
}