package com.macro.mall.searchmodern.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.macro.mall.searchmodern.dao.EsProductDao;
import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductRelatedInfo;
import com.macro.mall.searchmodern.repository.EsProductRepository;
import com.macro.mall.searchmodern.service.EmbeddingService;
import com.macro.mall.searchmodern.service.EsProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EsProductServiceImpl implements EsProductService {
    private static final Logger log = LoggerFactory.getLogger(EsProductServiceImpl.class);

    private final EsProductDao productDao;
    private final EsProductRepository productRepository;
    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;

    public EsProductServiceImpl(EsProductDao productDao,
                               EsProductRepository productRepository,
                               ElasticsearchClient esClient,
                               EmbeddingService embeddingService) {
        this.productDao = productDao;
        this.productRepository = productRepository;
        this.esClient = esClient;
        this.embeddingService = embeddingService;
    }

    @Override
    public int importAll() {
        List<EsProduct> esProductList = productDao.getAllEsProductList(null);
        if (esProductList.isEmpty()) {
            return 0;
        }

        // Generate embedding vectors for product names
        List<String> names = esProductList.stream()
                .map(EsProduct::getName)
                .toList();
        List<float[]> vectors = embeddingService.embedBatch(names);
        for (int i = 0; i < esProductList.size(); i++) {
            esProductList.get(i).setNameVector(vectors.get(i));
        }

        Iterable<EsProduct> saved = productRepository.saveAll(esProductList);
        int count = 0;
        for (var ignored : saved) {
            count++;
        }
        return count;
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public EsProduct create(Long id) {
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (!esProductList.isEmpty()) {
            EsProduct product = esProductList.getFirst();
            product.setNameVector(embeddingService.embed(product.getName()));
            return productRepository.save(product);
        }
        return null;
    }

    @Override
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<EsProduct> toDelete = new ArrayList<>();
        for (Long id : ids) {
            var p = new EsProduct();
            p.setId(id);
            toDelete.add(p);
        }
        productRepository.deleteAll(toDelete);
    }

    @Override
    public Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        return productRepository.findByNameOrSubTitleOrKeywords(keyword, keyword, keyword, pageable);
    }

    @Override
    public Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId,
                                  Integer pageNum, Integer pageSize, Integer sort) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        return doSearch(buildSearchQuery(keyword, brandId, productCategoryId, sort), pageable);
    }

    @Override
    public Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        EsProduct product = esProductList.getFirst();
        String kw = product.getName();
        Long brId = product.getBrandId();
        Long catId = product.getProductCategoryId();

        List<FunctionScore> scoreFunctions = List.of(
                FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("name").query(kw)))).weight(8.0)),
                FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("subTitle").query(kw)))).weight(2.0)),
                FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("keywords").query(kw)))).weight(2.0)),
                FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("brandId").query(brId)))).weight(5.0)),
                FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("productCategoryId").query(catId)))).weight(3.0))
        );

        Query scoreQuery = Query.of(q -> q.functionScore(fs -> fs
                .query(Query.of(q2 -> q2.bool(b -> b
                        .should(s -> s.match(m -> m.field("name").query(kw)))
                        .should(s -> s.match(m -> m.field("subTitle").query(kw)))
                        .should(s -> s.match(m -> m.field("keywords").query(kw)))
                        .should(s -> s.match(m -> m.field("brandId").query(brId)))
                        .should(s -> s.match(m -> m.field("productCategoryId").query(catId)))
                )))
                .functions(scoreFunctions)
                .scoreMode(FunctionScoreMode.Sum)
                .minScore(2.0)
        ));

        Query filterQuery = Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.term(t -> t.field("id").value(id)))));

        Query finalQuery = Query.of(q -> q.bool(b -> b.must(scoreQuery).filter(filterQuery)));

        return doSearch(finalQuery, pageable);
    }

    @Override
    public EsProductRelatedInfo searchRelatedInfo(String keyword) {
        Query query;
        if (keyword == null || keyword.isBlank()) {
            query = Query.of(q -> q.matchAll(m -> m));
        } else {
            query = Query.of(q -> q.multiMatch(mm -> mm
                    .fields("name", "subTitle", "keywords")
                    .query(keyword)));
        }

        try {
            SearchResponse<Void> response = esClient.search(s -> s
                            .index("pms")
                            .size(0)
                            .query(query)
                            .aggregations("brandNames", a -> a.terms(t -> t.field("brandName")))
                            .aggregations("productCategoryNames", a -> a.terms(t -> t.field("productCategoryName")))
                            .aggregations("allAttrValues", a -> a
                                    .nested(n -> n.path("attrValueList"))
                                    .aggregations("productAttrs", aa -> aa
                                            .filter(f -> f.term(t -> t.field("attrValueList.type").value(1)))
                                            .aggregations("attrIds", aaa -> aaa
                                                    .terms(t -> t.field("attrValueList.productAttributeId"))
                                                    .aggregations("attrValues", a4 -> a4.terms(t -> t.field("attrValueList.value")))
                                                    .aggregations("attrNames", a4 -> a4.terms(t -> t.field("attrValueList.name")))
                                            )
                                    )
                            ),
                    Void.class
            );
            return parseRelatedInfo(response);
        } catch (IOException e) {
            throw new RuntimeException("ES aggregation search failed", e);
        }
    }

    @Override
    public Page<EsProduct> searchSemantic(String keyword, Integer pageNum, Integer pageSize) {
        if (keyword == null || keyword.isBlank()) {
            return search(keyword, pageNum, pageSize);
        }

        Pageable pageable = PageRequest.of(pageNum, pageSize);
        float[] queryVector = embeddingService.embed(keyword);

        try {
            List<Float> queryVectorList = new ArrayList<>();
            for (float v : queryVector) {
                queryVectorList.add(v);
            }

            SearchResponse<EsProduct> response = esClient.search(s -> s
                            .index("pms")
                            .size(pageable.getPageSize())
                            .from((int) pageable.getOffset())
                            .query(buildKeywordQuery(keyword))
                            .knn(k -> k
                                    .field("nameVector")
                                    .queryVector(queryVectorList)
                                    .numCandidates(50)
                                    .k(pageSize)
                            )
                    , EsProduct.class);

            List<EsProduct> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageImpl<>(content, pageable, total);
        } catch (IOException e) {
            log.error("Semantic search failed, falling back to text search", e);
            return search(keyword, pageNum, pageSize);
        }
    }

    // --- private helpers ---

    private Query buildKeywordQuery(String keyword) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m.field("name").query(keyword).boost(10.0f)))
                .should(s -> s.match(m -> m.field("subTitle").query(keyword).boost(5.0f)))
                .should(s -> s.match(m -> m.field("keywords").query(keyword).boost(2.0f)))
                .minimumShouldMatch("1")
        ));
    }

    private Query buildSearchQuery(String keyword, Long brandId, Long productCategoryId, Integer sort) {
        // Build filter
        List<Query> filters = new ArrayList<>();
        if (brandId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("brandId").value(brandId))));
        }
        if (productCategoryId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("productCategoryId").value(productCategoryId))));
        }

        // Build main query
        Query mainQuery;
        if (keyword == null || keyword.isBlank()) {
            mainQuery = Query.of(q -> q.matchAll(m -> m));
        } else {
            List<FunctionScore> scoreFunctions = List.of(
                    FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("name").query(keyword)))).weight(10.0)),
                    FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("subTitle").query(keyword)))).weight(5.0)),
                    FunctionScore.of(f -> f.filter(Query.of(q2 -> q2.match(m -> m.field("keywords").query(keyword)))).weight(2.0))
            );

            mainQuery = Query.of(q -> q.functionScore(fs -> fs
                    .query(Query.of(q2 -> q2.bool(b -> b
                            .should(s -> s.match(m -> m.field("name").query(keyword)))
                            .should(s -> s.match(m -> m.field("subTitle").query(keyword)))
                            .should(s -> s.match(m -> m.field("keywords").query(keyword)))
                    )))
                    .functions(scoreFunctions)
                    .scoreMode(FunctionScoreMode.Sum)
                    .minScore(2.0)
            ));
        }

        // Combine filter + main query
        if (filters.isEmpty()) {
            return mainQuery;
        }
        return Query.of(q -> q.bool(b -> {
            b.must(mainQuery);
            filters.forEach(b::filter);
            return b;
        }));
    }

    private List<SortOptions> buildSortOptions(Integer sort) {
        List<SortOptions> sorts = new ArrayList<>();
        switch (sort) {
            case 1 -> sorts.add(SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("id").order(SortOrder.Desc)))));
            case 2 -> sorts.add(SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("sale").order(SortOrder.Desc)))));
            case 3 -> sorts.add(SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("price").order(SortOrder.Asc)))));
            case 4 -> sorts.add(SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("price").order(SortOrder.Desc)))));
            default -> { /* relevance (score) is default */ }
        }
        // Always add score sort as tiebreaker
        sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        return sorts;
    }

    private Page<EsProduct> doSearch(Query query, Pageable pageable) {
        var request = SearchRequest.of(s -> s
                .index("pms")
                .query(query)
                .sort(buildSortOptions(0))
                .from((int) pageable.getOffset())
                .size(pageable.getPageSize())
        );

        try {
            SearchResponse<EsProduct> response = esClient.search(request, EsProduct.class);
            List<EsProduct> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageImpl<>(content, pageable, total);
        } catch (IOException e) {
            throw new RuntimeException("ES search failed", e);
        }
    }

    private EsProductRelatedInfo parseRelatedInfo(SearchResponse<Void> response) {
        var aggMap = response.aggregations();

        // Brand names
        List<String> brandNames = new ArrayList<>();
        Aggregate brandAgg = aggMap.get("brandNames");
        if (brandAgg != null && brandAgg.isSterms()) {
            for (StringTermsBucket bucket : brandAgg.sterms().buckets().array()) {
                brandNames.add(bucket.key().stringValue());
            }
        }

        // Category names
        List<String> categoryNames = new ArrayList<>();
        Aggregate catAgg = aggMap.get("productCategoryNames");
        if (catAgg != null && catAgg.isSterms()) {
            for (StringTermsBucket bucket : catAgg.sterms().buckets().array()) {
                categoryNames.add(bucket.key().stringValue());
            }
        }

        // Attributes (nested -> filter -> terms attrIds -> terms attrValues + attrNames)
        List<EsProductRelatedInfo.ProductAttr> productAttrs = new ArrayList<>();
        Aggregate nestedAgg = aggMap.get("allAttrValues");
        if (nestedAgg != null && nestedAgg.isNested()) {
            Aggregate filterAgg = nestedAgg.nested().aggregations().get("productAttrs");
            if (filterAgg != null && filterAgg.isFilter()) {
                Aggregate attrIdsAgg = filterAgg.filter().aggregations().get("attrIds");
                if (attrIdsAgg != null && attrIdsAgg.isLterms()) {
                    for (LongTermsBucket attrIdBucket : attrIdsAgg.lterms().buckets().array()) {
                        Long attrId = attrIdBucket.key();
                        List<String> attrValues = new ArrayList<>();
                        String attrName = null;

                        Aggregate attrValuesAgg = attrIdBucket.aggregations().get("attrValues");
                        if (attrValuesAgg != null && attrValuesAgg.isSterms()) {
                            for (StringTermsBucket vBucket : attrValuesAgg.sterms().buckets().array()) {
                                attrValues.add(vBucket.key().stringValue());
                            }
                        }

                        Aggregate attrNamesAgg = attrIdBucket.aggregations().get("attrNames");
                        if (attrNamesAgg != null && attrNamesAgg.isSterms()) {
                            var nameBuckets = attrNamesAgg.sterms().buckets().array();
                            if (!nameBuckets.isEmpty()) {
                                attrName = nameBuckets.getFirst().key().stringValue();
                            }
                        }

                        productAttrs.add(new EsProductRelatedInfo.ProductAttr(attrId, attrName, attrValues));
                    }
                }
            }
        }

        return new EsProductRelatedInfo(brandNames, categoryNames, productAttrs);
    }
}
