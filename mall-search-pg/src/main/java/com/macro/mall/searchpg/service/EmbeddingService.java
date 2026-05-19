package com.macro.mall.searchpg.service;

import java.util.List;

public interface EmbeddingService {
    double[] embed(String text);
    List<double[]> embedBatch(List<String> texts);
    int dims();
}
