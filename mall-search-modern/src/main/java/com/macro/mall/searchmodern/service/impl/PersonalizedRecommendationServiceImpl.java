package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.dao.AiAnalysisDao;
import com.macro.mall.searchmodern.dao.EsProductDao;
import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.OrderInteraction;
import com.macro.mall.searchmodern.domain.ProductRecommendation;
import com.macro.mall.searchmodern.service.PersonalizedRecommendationService;
import com.macro.mall.searchmodern.service.RecommendationCache;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PersonalizedRecommendationServiceImpl implements PersonalizedRecommendationService {
    private static final int DEFAULT_SIZE = 5;
    private static final int MAX_SIZE = 20;

    private final AiAnalysisDao aiAnalysisDao;
    private final EsProductDao productDao;
    private final RecommendationCache recommendationCache;

    public PersonalizedRecommendationServiceImpl(AiAnalysisDao aiAnalysisDao,
                                                 EsProductDao productDao,
                                                 RecommendationCache recommendationCache) {
        this.aiAnalysisDao = aiAnalysisDao;
        this.productDao = productDao;
        this.recommendationCache = recommendationCache;
    }

    @Override
    public List<ProductRecommendation> recommend(Long memberId, Integer size) {
        int limit = safeLimit(size);
        Long safeMemberId = memberId == null ? 0L : memberId;
        return recommendationCache.get(safeMemberId)
                .filter(cached -> !cached.isEmpty())
                .map(cached -> first(cached, limit))
                .orElseGet(() -> {
                    List<ProductRecommendation> recommendations = buildRecommendations(safeMemberId);
                    recommendationCache.put(safeMemberId, recommendations);
                    return first(recommendations, limit);
                });
    }

    private List<ProductRecommendation> buildRecommendations(Long memberId) {
        Map<Long, EsProduct> productMap = loadActiveProducts();
        if (productMap.isEmpty()) {
            return List.of();
        }

        List<OrderInteraction> interactions = aiAnalysisDao.listOrderInteractions();
        Map<Long, List<OrderInteraction>> interactionsByOrder = groupByOrder(interactions);
        Map<Long, List<OrderInteraction>> interactionsByProduct = groupByProduct(interactions);
        Map<Long, Integer> productPopularity = countPopularity(interactions);

        List<OrderInteraction> memberInteractions = interactions.stream()
                .filter(interaction -> memberId.equals(interaction.getMemberId()))
                .toList();
        Set<Long> purchasedProductIds = productIds(memberInteractions);

        Map<Long, CandidateScore> scores = new HashMap<>();
        addCoPurchaseSignals(scores, memberInteractions, interactionsByProduct, interactionsByOrder, productMap, purchasedProductIds);
        addContentSignals(scores, memberInteractions, productMap, purchasedProductIds);
        addPopularitySignals(scores, productPopularity, productMap, purchasedProductIds, false);

        List<ProductRecommendation> recommendations = toRecommendations(scores, productMap);
        if (recommendations.size() < DEFAULT_SIZE) {
            addPopularitySignals(scores, productPopularity, productMap, purchasedProductIds, true);
            recommendations = toRecommendations(scores, productMap);
        }
        return first(recommendations, MAX_SIZE);
    }

    private Map<Long, EsProduct> loadActiveProducts() {
        Map<Long, EsProduct> productMap = new LinkedHashMap<>();
        for (EsProduct product : productDao.getAllEsProductList(null)) {
            if (product.getId() != null) {
                productMap.putIfAbsent(product.getId(), product);
            }
        }
        return productMap;
    }

    private void addCoPurchaseSignals(Map<Long, CandidateScore> scores,
                                      List<OrderInteraction> memberInteractions,
                                      Map<Long, List<OrderInteraction>> interactionsByProduct,
                                      Map<Long, List<OrderInteraction>> interactionsByOrder,
                                      Map<Long, EsProduct> productMap,
                                      Set<Long> purchasedProductIds) {
        Set<Long> memberProductIds = productIds(memberInteractions);
        for (Long productId : memberProductIds) {
            for (OrderInteraction seedInteraction : interactionsByProduct.getOrDefault(productId, List.of())) {
                for (OrderInteraction peer : interactionsByOrder.getOrDefault(seedInteraction.getOrderId(), List.of())) {
                    Long peerProductId = peer.getProductId();
                    if (peerProductId == null || peerProductId.equals(productId) || purchasedProductIds.contains(peerProductId)) {
                        continue;
                    }
                    EsProduct peerProduct = productMap.get(peerProductId);
                    if (peerProduct == null) {
                        continue;
                    }
                    score(scores, peerProductId)
                            .add(4.0, "CO_PURCHASED", "与用户已购商品经常同单出现");
                }
            }
        }
    }

    private void addContentSignals(Map<Long, CandidateScore> scores,
                                   List<OrderInteraction> memberInteractions,
                                   Map<Long, EsProduct> productMap,
                                   Set<Long> purchasedProductIds) {
        Map<String, Integer> brandWeights = new HashMap<>();
        Map<Long, Integer> categoryWeights = new HashMap<>();
        for (OrderInteraction interaction : memberInteractions) {
            int quantity = positiveQuantity(interaction);
            if (interaction.getProductBrand() != null && !interaction.getProductBrand().isBlank()) {
                brandWeights.merge(interaction.getProductBrand(), quantity, Integer::sum);
            }
            if (interaction.getProductCategoryId() != null) {
                categoryWeights.merge(interaction.getProductCategoryId(), quantity, Integer::sum);
            }
        }

        if (brandWeights.isEmpty() && categoryWeights.isEmpty()) {
            return;
        }

        for (EsProduct product : productMap.values()) {
            Long productId = product.getId();
            if (productId == null || purchasedProductIds.contains(productId)) {
                continue;
            }
            Integer brandWeight = brandWeights.get(product.getBrandName());
            if (brandWeight != null) {
                score(scores, productId).add(Math.min(12.0, 2.5 * brandWeight),
                        "SAME_BRAND", "匹配用户偏好的品牌：" + product.getBrandName());
            }
            Integer categoryWeight = categoryWeights.get(product.getProductCategoryId());
            if (categoryWeight != null) {
                score(scores, productId).add(Math.min(8.0, 2.0 * categoryWeight),
                        "SAME_CATEGORY", "匹配用户偏好的分类：" + product.getProductCategoryName());
            }
        }
    }

    private void addPopularitySignals(Map<Long, CandidateScore> scores,
                                      Map<Long, Integer> productPopularity,
                                      Map<Long, EsProduct> productMap,
                                      Set<Long> purchasedProductIds,
                                      boolean includePurchased) {
        for (EsProduct product : productMap.values()) {
            Long productId = product.getId();
            if (productId == null || (!includePurchased && purchasedProductIds.contains(productId))) {
                continue;
            }
            int orderQuantity = productPopularity.getOrDefault(productId, 0);
            int sale = product.getSale() == null ? 0 : product.getSale();
            double popularityScore = Math.log1p(orderQuantity) * 1.5 + Math.log1p(Math.max(sale, 0)) * 0.15;
            if (product.getRecommandStatus() != null && product.getRecommandStatus() == 1) {
                popularityScore += 1.5;
            }
            if (product.getNewStatus() != null && product.getNewStatus() == 1) {
                popularityScore += 0.5;
            }
            if (popularityScore <= 0.0) {
                popularityScore = 0.2;
            }

            CandidateScore candidateScore = score(scores, productId);
            if (purchasedProductIds.contains(productId)) {
                candidateScore.add(popularityScore * 0.35, "REPEAT_PURCHASE", "已购高频商品，可作为复购补充推荐");
            } else {
                candidateScore.add(popularityScore, "POPULAR", "近期订单和销量表现较好");
            }
        }
    }

    private List<ProductRecommendation> toRecommendations(Map<Long, CandidateScore> scores, Map<Long, EsProduct> productMap) {
        return scores.entrySet().stream()
                .filter(entry -> productMap.containsKey(entry.getKey()))
                .map(entry -> entry.getValue().toRecommendation(productMap.get(entry.getKey())))
                .sorted(Comparator
                        .comparingDouble(ProductRecommendation::score).reversed()
                        .thenComparing(recommendation -> recommendation.product().id()))
                .toList();
    }

    private static Map<Long, List<OrderInteraction>> groupByOrder(List<OrderInteraction> interactions) {
        Map<Long, List<OrderInteraction>> grouped = new HashMap<>();
        for (OrderInteraction interaction : interactions) {
            if (interaction.getOrderId() != null) {
                grouped.computeIfAbsent(interaction.getOrderId(), ignored -> new ArrayList<>()).add(interaction);
            }
        }
        return grouped;
    }

    private static Map<Long, List<OrderInteraction>> groupByProduct(List<OrderInteraction> interactions) {
        Map<Long, List<OrderInteraction>> grouped = new HashMap<>();
        for (OrderInteraction interaction : interactions) {
            if (interaction.getProductId() != null) {
                grouped.computeIfAbsent(interaction.getProductId(), ignored -> new ArrayList<>()).add(interaction);
            }
        }
        return grouped;
    }

    private static Map<Long, Integer> countPopularity(List<OrderInteraction> interactions) {
        Map<Long, Integer> popularity = new HashMap<>();
        for (OrderInteraction interaction : interactions) {
            if (interaction.getProductId() != null) {
                popularity.merge(interaction.getProductId(), positiveQuantity(interaction), Integer::sum);
            }
        }
        return popularity;
    }

    private static Set<Long> productIds(List<OrderInteraction> interactions) {
        Set<Long> productIds = new HashSet<>();
        for (OrderInteraction interaction : interactions) {
            if (interaction.getProductId() != null) {
                productIds.add(interaction.getProductId());
            }
        }
        return productIds;
    }

    private static CandidateScore score(Map<Long, CandidateScore> scores, Long productId) {
        return scores.computeIfAbsent(productId, ignored -> new CandidateScore());
    }

    private static int positiveQuantity(OrderInteraction interaction) {
        return interaction.getQuantity() == null || interaction.getQuantity() <= 0 ? 1 : interaction.getQuantity();
    }

    private static int safeLimit(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static List<ProductRecommendation> first(List<ProductRecommendation> recommendations, int limit) {
        if (recommendations.size() <= limit) {
            return recommendations;
        }
        return recommendations.subList(0, limit);
    }

    private static final class CandidateScore {
        private double score;
        private final Set<String> reasonTags = new LinkedHashSet<>();
        private final Set<String> reasons = new LinkedHashSet<>();

        private CandidateScore add(double delta, String reasonTag, String reason) {
            score += delta;
            reasonTags.add(reasonTag);
            reasons.add(reason);
            return this;
        }

        private ProductRecommendation toRecommendation(EsProduct product) {
            return new ProductRecommendation(
                    EsProductResponse.from(product),
                    Math.round(score * 100.0) / 100.0,
                    List.copyOf(reasonTags),
                    List.copyOf(reasons));
        }
    }
}
