package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.dao.AiAnalysisDao;
import com.macro.mall.searchmodern.dao.EsProductDao;
import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.OrderInteraction;
import com.macro.mall.searchmodern.domain.OrderSignal;
import com.macro.mall.searchmodern.domain.ProductRecommendation;
import com.macro.mall.searchmodern.service.RecommendationCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonalizedRecommendationServiceImpl 单元测试")
class PersonalizedRecommendationServiceImplTest {

    @Test
    @DisplayName("基于共购、品牌分类和热度生成推荐并写入缓存")
    void recommend_buildsPersonalizedResultsAndCaches() {
        FakeAiAnalysisDao analysisDao = new FakeAiAnalysisDao();
        FakeEsProductDao productDao = new FakeEsProductDao(List.of(
                product(1L, "小米12", "小米", 19L, "手机", 1000),
                product(2L, "Redmi K50", "小米", 19L, "手机", 800),
                product(3L, "小米笔记本", "小米", 54L, "笔记本", 300),
                product(4L, "Apple iPad", "苹果", 53L, "平板", 600),
                product(5L, "索尼耳机", "索尼", 60L, "耳机", 500),
                product(6L, "三星 SSD", "三星", 55L, "存储", 400)
        ));
        analysisDao.interactions = List.of(
                interaction(1L, 100L, 1L, "小米", 19L),
                interaction(2L, 200L, 1L, "小米", 19L),
                interaction(2L, 200L, 2L, "小米", 19L),
                interaction(2L, 201L, 1L, "小米", 19L),
                interaction(2L, 201L, 4L, "苹果", 53L),
                interaction(3L, 300L, 5L, "索尼", 60L),
                interaction(3L, 301L, 6L, "三星", 55L)
        );
        FakeRecommendationCache cache = new FakeRecommendationCache();
        PersonalizedRecommendationServiceImpl service =
                new PersonalizedRecommendationServiceImpl(analysisDao, productDao, cache);

        List<ProductRecommendation> result = service.recommend(1L, 5);

        assertThat(result).hasSize(5);
        assertThat(result.getFirst().product().id()).isEqualTo(2L);
        assertThat(result.getFirst().reasonTags()).contains("CO_PURCHASED", "SAME_BRAND", "SAME_CATEGORY");
        assertThat(result).extracting(recommendation -> recommendation.product().id())
                .doesNotContain(1L);
        assertThat(cache.stored).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("缓存命中时直接返回缓存结果")
    void recommend_cacheHitSkipsRebuild() {
        FakeAiAnalysisDao analysisDao = new FakeAiAnalysisDao();
        FakeEsProductDao productDao = new FakeEsProductDao(List.of(product(9L, "缓存商品", "品牌", 1L, "分类", 1)));
        FakeRecommendationCache cache = new FakeRecommendationCache();
        cache.cached = List.of(new ProductRecommendation(
                EsProductResponse.from(product(9L, "缓存商品", "品牌", 1L, "分类", 1)),
                10.0,
                List.of("POPULAR"),
                List.of("缓存命中")));
        PersonalizedRecommendationServiceImpl service =
                new PersonalizedRecommendationServiceImpl(analysisDao, productDao, cache);

        List<ProductRecommendation> result = service.recommend(1L, 5);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().product().id()).isEqualTo(9L);
        assertThat(analysisDao.interactionCalls).isZero();
        assertThat(productDao.calls).isZero();
    }

    private static EsProduct product(Long id, String name, String brand, Long categoryId, String categoryName, Integer sale) {
        EsProduct product = new EsProduct();
        product.setId(id);
        product.setName(name);
        product.setBrandName(brand);
        product.setBrandId(id + 100);
        product.setProductCategoryId(categoryId);
        product.setProductCategoryName(categoryName);
        product.setPrice(new BigDecimal("100.00"));
        product.setSale(sale);
        product.setRecommandStatus(1);
        product.setNewStatus(0);
        return product;
    }

    private static OrderInteraction interaction(Long memberId, Long orderId, Long productId, String brand, Long categoryId) {
        OrderInteraction interaction = new OrderInteraction();
        interaction.setMemberId(memberId);
        interaction.setOrderId(orderId);
        interaction.setProductId(productId);
        interaction.setProductBrand(brand);
        interaction.setProductCategoryId(categoryId);
        interaction.setQuantity(1);
        interaction.setCreateTime(LocalDateTime.now());
        return interaction;
    }

    private static class FakeAiAnalysisDao implements AiAnalysisDao {
        private List<OrderInteraction> interactions = new ArrayList<>();
        private int interactionCalls;

        @Override
        public List<OrderInteraction> listOrderInteractions() {
            interactionCalls++;
            return interactions;
        }

        @Override
        public List<OrderSignal> listOrdersInAnalysisWindow(Integer days) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalDateTime getOrderAnalysisWindowEnd() {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeEsProductDao implements EsProductDao {
        private final List<EsProduct> products;
        private int calls;

        private FakeEsProductDao(List<EsProduct> products) {
            this.products = products;
        }

        @Override
        public List<EsProduct> getAllEsProductList(Long id) {
            calls++;
            return products;
        }
    }

    private static class FakeRecommendationCache implements RecommendationCache {
        private List<ProductRecommendation> cached;
        private List<ProductRecommendation> stored = List.of();

        @Override
        public Optional<List<ProductRecommendation>> get(Long memberId) {
            return Optional.ofNullable(cached);
        }

        @Override
        public void put(Long memberId, List<ProductRecommendation> recommendations) {
            stored = recommendations;
        }
    }
}
