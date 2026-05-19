package com.macro.mall.searchpg.controller;

import com.macro.mall.searchpg.domain.HybridSearchResult;
import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;
import com.macro.mall.searchpg.service.VectorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "向量搜索", description = "基于 pgvector 的语义搜索")
@RestController
@RequestMapping("/vector")
@RequiredArgsConstructor
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    public record InitResponse(String message) {}

    @Operation(summary = "初始化数据库")
    @PostMapping("/init")
    public InitResponse initDatabase() {
        vectorSearchService.initDatabase();
        return new InitResponse("Database initialized successfully");
    }

    @Data
    public static class IndexRequest {
        private Long productId;
        private String name;
        private String description;
        private String category;
        private String brand;
    }

    @Operation(summary = "索引单个商品")
    @PostMapping("/index")
    public String indexProduct(@RequestBody IndexRequest request) {
        ProductEmbedding product = ProductEmbedding.builder()
                .productId(request.getProductId())
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .brand(request.getBrand())
                .build();
        vectorSearchService.indexProduct(product);
        return "Product indexed: " + request.getProductId();
    }

    @Operation(summary = "批量索引商品")
    @PostMapping("/index/batch")
    public String batchIndexProducts(@RequestBody List<IndexRequest> requests) {
        List<ProductEmbedding> products = requests.stream()
                .map(req -> ProductEmbedding.builder()
                        .productId(req.getProductId())
                        .name(req.getName())
                        .description(req.getDescription())
                        .category(req.getCategory())
                        .brand(req.getBrand())
                        .build())
                .toList();
        vectorSearchService.batchIndexProducts(products);
        return "Indexed " + products.size() + " products";
    }

    public record SearchResponse(String query, List<SimilarityResult> results) {}

    @Operation(summary = "语义搜索")
    @GetMapping("/search")
    public SearchResponse search(
            @Parameter(description = "搜索查询") @RequestParam String query,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int limit) {
        List<SimilarityResult> results = vectorSearchService.semanticSearch(query, limit);
        return new SearchResponse(query, results);
    }

    public record HybridSearchResponse(String query, List<HybridSearchResult> results) {}

    @Operation(summary = "混合搜索")
    @GetMapping("/search/hybrid")
    public HybridSearchResponse hybridSearch(
            @Parameter(description = "搜索查询") @RequestParam String query,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "向量权重") @RequestParam(defaultValue = "0.7") double vectorWeight,
            @Parameter(description = "关键词权重") @RequestParam(defaultValue = "0.3") double textWeight) {
        List<HybridSearchResult> results = vectorSearchService.hybridSearch(query, limit, vectorWeight, textWeight);
        return new HybridSearchResponse(query, results);
    }

    public record StatsResponse(int totalProducts) {}

    @Operation(summary = "统计信息")
    @GetMapping("/stats")
    public StatsResponse stats() {
        return new StatsResponse(vectorSearchService.getProductCount());
    }

    @Operation(summary = "从 MySQL 同步商品数据到向量库")
    @PostMapping("/sync")
    public SyncResponse syncFromMysql() {
        int count = vectorSearchService.syncFromMysql();
        return new SyncResponse("Synced " + count + " products", count);
    }

    public record SyncResponse(String message, int count) {}

    @Operation(summary = "获取所有商品")
    @GetMapping("/products")
    public List<ProductEmbedding> getAllProducts() {
        return vectorSearchService.getAllProducts();
    }
}
