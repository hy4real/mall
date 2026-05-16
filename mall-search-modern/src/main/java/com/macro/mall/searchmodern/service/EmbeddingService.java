package com.macro.mall.searchmodern.service;

import java.util.List;

public interface EmbeddingService {

    /**
     * Generate embedding vector for a single text.
     * Returns zero vector if the API is unavailable.
     */
    float[] embed(String text);

    /**
     * Generate embedding vectors for a batch of texts.
     * The returned list order corresponds to the input order.
     * Each element is a zero vector if the API is unavailable.
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Returns the configured vector dimension.
     */
    int dims();
}
