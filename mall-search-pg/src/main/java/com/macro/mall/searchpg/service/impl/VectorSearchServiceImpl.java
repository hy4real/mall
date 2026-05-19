package com.macro.mall.searchpg.service.impl;

import com.macro.mall.searchpg.domain.ProductEmbedding;
import com.macro.mall.searchpg.domain.SimilarityResult;
import com.macro.mall.searchpg.reader.MysqlProductReader;
import com.macro.mall.searchpg.reader.MysqlProductReader.ProductRow;
import com.macro.mall.searchpg.repository.ProductEmbeddingRepository;
import com.macro.mall.searchpg.service.EmbeddingService;
import com.macro.mall.searchpg.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchServiceImpl.class);

    private final ProductEmbeddingRepository repository;
    private final EmbeddingService embeddingService;
    private final MysqlProductReader mysqlProductReader;

    public VectorSearchServiceImpl(ProductEmbeddingRepository repository,
                                   EmbeddingService embeddingService,
                                   MysqlProductReader mysqlProductReader) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.mysqlProductReader = mysqlProductReader;
    }

    @Override
    public void initDatabase() {
        repository.initSchema(embeddingService.dims());
        log.info("Database schema initialized ({} dims)", embeddingService.dims());
    }

    @Override
    public void indexProduct(ProductEmbedding product) {
        String text = productText(product);
        product.setEmbedding(embeddingService.embed(text));
        repository.insert(product);
        log.info("Indexed product: {}", product.getProductId());
    }

    @Override
    public void batchIndexProducts(List<ProductEmbedding> products) {
        if (products.isEmpty()) return;
        List<String> texts = products.stream()
                .map(this::productText)
                .toList();
        List<double[]> embeddings = embeddingService.embedBatch(texts);
        for (int i = 0; i < products.size(); i++) {
            products.get(i).setEmbedding(embeddings.get(i));
        }
        repository.batchInsert(products);
        log.info("Batch indexed {} products", products.size());
    }

    @Override
    public List<SimilarityResult> semanticSearch(String query, int limit) {
        double[] queryVec = embeddingService.embed(query);
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

    @Override
    public int syncFromMysql() {
        List<ProductRow> rows = mysqlProductReader.readAllProducts();
        List<ProductEmbedding> products = new ArrayList<>(rows.size());
        for (ProductRow row : rows) {
            products.add(ProductEmbedding.builder()
                    .productId(row.id())
                    .name(row.name())
                    .description(buildDescription(row))
                    .category(row.categoryName())
                    .brand(row.brandName())
                    .build());
        }
        batchIndexProducts(products);
        log.info("Synced {} products from MySQL", products.size());
        return products.size();
    }

    private String productText(ProductEmbedding product) {
        StringBuilder sb = new StringBuilder(product.getName());
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            sb.append(' ').append(product.getDescription());
        }
        return sb.toString();
    }

    private String buildDescription(ProductRow row) {
        StringBuilder sb = new StringBuilder();
        if (row.subTitle() != null && !row.subTitle().isBlank()) {
            sb.append(row.subTitle());
        }
        if (row.keywords() != null && !row.keywords().isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(row.keywords());
        }
        return sb.toString();
    }
}