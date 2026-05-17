package com.macro.mall.search.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.macro.mall.search.dao.EsProductDao;
import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.domain.EsProductRelatedInfo;
import com.macro.mall.search.repository.EsProductRepository;
import com.macro.mall.search.service.EsProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 搜索商品管理Service实现类
 * Created by macro on 2018/6/19.
 */
@Service
@RequiredArgsConstructor
public class EsProductServiceImpl implements EsProductService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EsProductServiceImpl.class);
    private final EsProductDao productDao;
    private final EsProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public int importAll() {
        List<EsProduct> esProductList = productDao.getAllEsProductList(null);
        Iterable<EsProduct> esProductIterable = productRepository.saveAll(esProductList);
        Iterator<EsProduct> iterator = esProductIterable.iterator();
        int result = 0;
        while (iterator.hasNext()) {
            result++;
            iterator.next();
        }
        return result;
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public EsProduct create(Long id) {
        EsProduct result = null;
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            result = productRepository.save(esProduct);
        }
        return result;
    }

    @Override
    public void delete(List<Long> ids) {
        if (!CollectionUtils.isEmpty(ids)) {
            List<EsProduct> esProductList = new ArrayList<>();
            for (Long id : ids) {
                EsProduct esProduct = new EsProduct();
                esProduct.setId(id);
                esProductList.add(esProduct);
            }
            productRepository.deleteAll(esProductList);
        }
    }

    @Override
    public Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        return productRepository.findByNameOrSubTitleOrKeywords(keyword, keyword, keyword, pageable);
    }

    @Override
    public Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        NativeQueryBuilder queryBuilder = NativeQuery.builder().withPageable(pageable);

        // build filter
        List<Query> filterClauses = new ArrayList<>();
        if (brandId != null) {
            Query brandFilter = Query.of(b -> b.term(t -> t.field("brandId").value(brandId)));
            filterClauses.add(brandFilter);
        }
        if (productCategoryId != null) {
            Query categoryFilter = Query.of(c -> c.term(t -> t.field("productCategoryId").value(productCategoryId)));
            filterClauses.add(categoryFilter);
        }
        if (!filterClauses.isEmpty()) {
            queryBuilder.withFilter(Query.of(q -> q.bool(BoolQuery.of(b -> b.filter(filterClauses)))));
        }

        // build search query
        Query searchQuery = buildSearchQuery(keyword);
        queryBuilder.withQuery(searchQuery);

        // build sort
        List<SortOptions> sortOptions = new ArrayList<>();
        sortOptions.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        sortOptions.addAll(getSortOptions(sort));
        queryBuilder.withSort(sortOptions);

        NativeQuery nativeQuery = queryBuilder.build();
        LOGGER.info("ES search query: {}", nativeQuery.getQuery());
        SearchHits<EsProduct> searchHits = elasticsearchOperations.search(nativeQuery, EsProduct.class);
        if (searchHits.getTotalHits() <= 0) {
            return new PageImpl<>(ListUtil.empty(), pageable, 0);
        }
        List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        return new PageImpl<>(searchProductList, pageable, searchHits.getTotalHits());
    }

    @Override
    public Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            String keyword = esProduct.getName();
            Long brandId = esProduct.getBrandId();
            Long productCategoryId = esProduct.getProductCategoryId();

            Query functionScoreQuery = buildRecommendQuery(keyword, brandId, productCategoryId);

            // exclude the same product
            Query excludeSelf = Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.term(t -> t.field("id").value(id)))));
            Query filterQuery = excludeSelf;

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(functionScoreQuery)
                    .withFilter(filterQuery)
                    .withPageable(pageable)
                    .build();

            LOGGER.info("ES recommend query: {}", nativeQuery.getQuery());
            SearchHits<EsProduct> searchHits = elasticsearchOperations.search(nativeQuery, EsProduct.class);
            if (searchHits.getTotalHits() <= 0) {
                return new PageImpl<>(ListUtil.empty(), pageable, 0);
            }
            List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
            return new PageImpl<>(searchProductList, pageable, searchHits.getTotalHits());
        }
        return new PageImpl<>(ListUtil.empty());
    }

    @Override
    public EsProductRelatedInfo searchRelatedInfo(String keyword) {
        // TODO: Aggregation support needs to be reimplemented with co.elastic.clients aggregations API
        // For now, return empty result structure
        return new EsProductRelatedInfo();
    }

    private Query buildSearchQuery(String keyword) {
        if (StrUtil.isEmpty(keyword)) {
            return Query.of(q -> q.matchAll(ma -> ma));
        }
        List<FunctionScore> functions = List.of(
                FunctionScore.of(f -> f.filter(fn -> fn.match(m -> m.field("name").query(keyword))).weight(10.0)),
                FunctionScore.of(f -> f.filter(fn -> fn.match(m -> m.field("subTitle").query(keyword))).weight(5.0)),
                FunctionScore.of(f -> f.filter(fn -> fn.match(m -> m.field("keywords").query(keyword))).weight(2.0))
        );
        return Query.of(q -> q.functionScore(fs -> fs
                .query(Query.of(mq -> mq.matchAll(ma -> ma)))
                .functions(functions)
                .scoreMode(FunctionScoreMode.Sum)
                .boostMode(FunctionBoostMode.Multiply)
                .minScore(2.0)
        ));
    }

    private Query buildRecommendQuery(String keyword, Long brandId, Long productCategoryId) {
        List<FunctionScore> functions = List.of(
                FunctionScore.of(f -> f.filter(fn -> fn.match(m -> m.field("name").query(keyword))).weight(8.0)),
                FunctionScore.of(f -> f.filter(fn -> fn.match(m -> m.field("subTitle").query(keyword))).weight(2.0)),
                FunctionScore.of(f -> f.filter(fn -> fn.match(m -> m.field("keywords").query(keyword))).weight(2.0)),
                FunctionScore.of(f -> f.filter(fn -> fn.term(t -> t.field("brandId").value(brandId))).weight(5.0)),
                FunctionScore.of(f -> f.filter(fn -> fn.term(t -> t.field("productCategoryId").value(productCategoryId))).weight(3.0))
        );
        return Query.of(q -> q.functionScore(fs -> fs
                .query(Query.of(mq -> mq.matchAll(ma -> ma)))
                .functions(functions)
                .scoreMode(FunctionScoreMode.Sum)
                .boostMode(FunctionBoostMode.Multiply)
                .minScore(2.0)
        ));
    }

    private List<SortOptions> getSortOptions(Integer sort) {
        if (sort == null || sort == 0) {
            return List.of();
        }
        String field = switch (sort) {
            case 1 -> "id";
            case 2 -> "sale";
            case 3, 4 -> "price";
            default -> null;
        };
        if (field == null) {
            return List.of();
        }
        SortOrder order = (sort == 3) ? SortOrder.Asc : SortOrder.Desc;
        return List.of(SortOptions.of(s -> s.field(FieldSort.of(f -> f.field(field).order(order)))));
    }
}
