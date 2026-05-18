package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.RagResponse;
import com.macro.mall.searchmodern.service.ChatService;
import com.macro.mall.searchmodern.service.EsProductService;
import com.macro.mall.searchmodern.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagServiceImpl implements RagService {
    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);
    private static final String OFF_TOPIC_MESSAGE = "我只能基于商城商品库回答导购和商品推荐问题。请告诉我想买的商品类型、预算或偏好。";
    private static final String NO_PRODUCTS_MESSAGE = "抱歉，没有找到与您问题相关的商品，请尝试其他关键词。";
    private static final Pattern MAX_BUDGET_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块)?\\s*(?:以内|以下|内|之内|以下的|以内的)");
    private static final Map<String, List<String>> CATEGORY_TERMS = new LinkedHashMap<>();
    private static final Map<String, List<String>> FEATURE_TERMS = new LinkedHashMap<>();
    private static final List<String> SHOPPING_TERMS = List.of(
            "买", "购买", "推荐", "商品", "产品", "价格", "预算", "多少钱", "哪款", "哪几款",
            "适合", "对比", "品牌", "促销", "优惠", "手机", "耳机", "电脑", "电视", "家电",
            "热水器", "短袖", "t恤", "T恤", "衣服", "外套", "羽绒服", "鞋", "男鞋",
            "女鞋", "运动", "拍照", "续航", "游戏", "保暖", "夏天", "冬天", "笔记本");

    static {
        CATEGORY_TERMS.put("手机", List.of("手机", "手机通讯", "5g", "4g", "全网通", "双卡"));
        CATEGORY_TERMS.put("耳机", List.of("耳机", "蓝牙耳机", "降噪"));
        CATEGORY_TERMS.put("短袖", List.of("短袖", "t恤", "T恤", "tee"));
        CATEGORY_TERMS.put("鞋", List.of("鞋", "男鞋", "女鞋", "运动鞋", "休闲鞋"));
        CATEGORY_TERMS.put("热水器", List.of("热水器", "厨卫大电", "燃气"));
        CATEGORY_TERMS.put("电视", List.of("电视", "平板电视", "液晶电视"));
        CATEGORY_TERMS.put("笔记本", List.of("笔记本", "电脑", "book", "laptop"));
        CATEGORY_TERMS.put("外套", List.of("外套", "羽绒服", "保暖"));
        CATEGORY_TERMS.put("家电", List.of("家电", "电视", "热水器", "厨卫"));

        FEATURE_TERMS.put("拍照", List.of("拍照", "摄影", "相机", "镜头", "影像", "摄", "防抖", "变焦"));
        FEATURE_TERMS.put("续航", List.of("续航", "电池", "毫安", "mah", "mAh", "大电量", "待机"));
        FEATURE_TERMS.put("游戏", List.of("游戏", "处理器", "骁龙", "天玑", "高刷"));
        FEATURE_TERMS.put("保暖", List.of("保暖", "羽绒", "秋冬", "冬"));
    }

    private static final String SYSTEM_PROMPT = """
            你是商城智能导购助手。请根据以下商品信息回答用户的问题。

            规则：
            1. 只基于下方给定的商品来推荐，不要编造商品
            2. 如果没有匹配的商品，如实告知用户没有找到相关商品
            3. 推荐时列出商品名称、价格、品牌等关键信息
            4. 回答简洁，控制在200字以内
            5. 不要补充商品信息中没有出现的参数、卖点或承诺
            6. 如果用户问题与商品无关，礼貌引导到购物话题
            """;

    private final EsProductService esProductService;
    private final ChatService chatService;

    public RagServiceImpl(EsProductService esProductService, ChatService chatService) {
        this.esProductService = esProductService;
        this.chatService = chatService;
    }

    @Override
    public RagResponse ask(String question) {
        if (question == null || question.isBlank()) {
            return new RagResponse(NO_PRODUCTS_MESSAGE, List.of());
        }
        if (!isShoppingQuestion(question)) {
            return new RagResponse(OFF_TOPIC_MESSAGE, List.of());
        }

        Page<EsProduct> result = esProductService.searchSemantic(question, 0, 5);
        List<EsProduct> products = filterRelevantProducts(question, result.getContent());

        if (products.isEmpty()) {
            return new RagResponse(NO_PRODUCTS_MESSAGE, List.of());
        }

        String context = buildContext(products);
        String systemPromptWithContext = SYSTEM_PROMPT + "\n\n当前可推荐商品：\n" + context;

        log.debug("RAG system prompt ({} chars), {} products", systemPromptWithContext.length(), products.size());
        String answer = chatService.chat(systemPromptWithContext, question);
        return new RagResponse(answer, products.stream().map(EsProductResponse::from).toList());
    }

    private String buildContext(List<EsProduct> products) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            EsProduct p = products.get(i);
            sb.append(i + 1).append(". ");
            sb.append(p.getName());
            if (p.getBrandName() != null && !p.getBrandName().isBlank()) {
                sb.append("（品牌：").append(p.getBrandName()).append("）");
            }
            sb.append(" — ¥").append(p.getPrice());
            if (p.getSubTitle() != null && !p.getSubTitle().isBlank()) {
                sb.append("，").append(p.getSubTitle());
            }
            if (p.getSale() != null && p.getSale() > 0) {
                sb.append("，销量：").append(p.getSale());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private boolean isShoppingQuestion(String question) {
        String normalized = question.trim();
        return SHOPPING_TERMS.stream().anyMatch(normalized::contains);
    }

    private List<EsProduct> filterRelevantProducts(String question, List<EsProduct> products) {
        List<String> categoryTerms = requestedCategoryTerms(question);
        List<String> featureTerms = requestedFeatureTerms(question);
        Optional<BigDecimal> maxBudget = extractMaxBudget(question);
        List<EsProduct> filtered = new ArrayList<>();
        for (EsProduct product : products) {
            if (matchesRequestedCategory(product, categoryTerms)
                    && matchesRequestedFeature(product, featureTerms)
                    && withinBudget(product, maxBudget)) {
                filtered.add(product);
            }
        }
        return filtered;
    }

    private List<String> requestedCategoryTerms(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : CATEGORY_TERMS.entrySet()) {
            if (containsAny(normalized, entry.getKey(), entry.getValue())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    private List<String> requestedFeatureTerms(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : FEATURE_TERMS.entrySet()) {
            if (containsAny(normalized, entry.getKey(), entry.getValue())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    private boolean containsAny(String normalizedText, String key, List<String> terms) {
        if (normalizedText.contains(key.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return terms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedText::contains);
    }

    private boolean matchesRequestedCategory(EsProduct product, List<String> categoryTerms) {
        if (categoryTerms.isEmpty()) {
            return true;
        }
        String productText = productText(product).toLowerCase(Locale.ROOT);
        return categoryTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(productText::contains);
    }

    private boolean matchesRequestedFeature(EsProduct product, List<String> featureTerms) {
        if (featureTerms.isEmpty()) {
            return true;
        }
        String productText = productText(product).toLowerCase(Locale.ROOT);
        return featureTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(productText::contains);
    }

    private String productText(EsProduct product) {
        StringBuilder sb = new StringBuilder();
        appendIfNotBlank(sb, product.getName());
        appendIfNotBlank(sb, product.getSubTitle());
        appendIfNotBlank(sb, product.getKeywords());
        appendIfNotBlank(sb, product.getBrandName());
        appendIfNotBlank(sb, product.getProductCategoryName());
        if (product.getAttrValueList() != null) {
            product.getAttrValueList().forEach(attr -> {
                appendIfNotBlank(sb, attr.getName());
                appendIfNotBlank(sb, attr.getValue());
            });
        }
        return sb.toString();
    }

    private Optional<BigDecimal> extractMaxBudget(String question) {
        Matcher matcher = MAX_BUDGET_PATTERN.matcher(question);
        if (matcher.find()) {
            return Optional.of(new BigDecimal(matcher.group(1)));
        }
        return Optional.empty();
    }

    private boolean withinBudget(EsProduct product, Optional<BigDecimal> maxBudget) {
        if (maxBudget.isEmpty() || product.getPrice() == null) {
            return true;
        }
        return product.getPrice().compareTo(maxBudget.get()) <= 0;
    }

    private static void appendIfNotBlank(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(' ').append(value);
        }
    }
}
