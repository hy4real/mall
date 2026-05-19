package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductRelatedInfo;
import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.RagResponse;
import com.macro.mall.searchmodern.service.ChatService;
import com.macro.mall.searchmodern.service.EsProductService;
import com.macro.mall.searchmodern.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagServiceImpl 单元测试")
class RagServiceImplTest {

    private FakeEsProductService esProductService;
    private FakeChatService chatService;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        esProductService = new FakeEsProductService();
        chatService = new FakeChatService();
        ragService = new RagServiceImpl(esProductService, chatService);
    }

    @Nested
    @DisplayName("ask - RAG 问答")
    class AskTests {

        @Test
        @DisplayName("搜索到商品时调用 LLM 并返回回答")
        void ask_withProducts_callsLLM() {
            var product = new EsProduct();
            product.setName("无线蓝牙耳机");
            product.setPrice(new BigDecimal("149.00"));
            product.setBrandName("索尼");
            product.setSubTitle("降噪耳机 续航30小时");
            product.setSale(1000);
            esProductService.semanticResult = pageOf(List.of(product));
            chatService.answer = "为您推荐索尼无线蓝牙耳机，售价149元";

            RagResponse result = ragService.ask("推荐耳机");

            assertThat(result.answer()).contains("索尼");
            assertThat(result.sourceProducts()).hasSize(1);
            assertThat(result.sourceProducts().get(0).name()).isEqualTo("无线蓝牙耳机");
            assertThat(chatService.chatCalls).isEqualTo(1);
            assertThat(chatService.userMessage).isEqualTo("推荐耳机");
        }

        @Test
        @DisplayName("系统提示包含商品上下文信息")
        void ask_contextIncludesProductInfo() {
            var product = new EsProduct();
            product.setName("轻薄羽绒服");
            product.setPrice(new BigDecimal("299.00"));
            product.setBrandName("优衣库");
            product.setSubTitle("秋冬保暖 可收纳");
            product.setSale(500);
            esProductService.semanticResult = pageOf(List.of(product));

            ragService.ask("冬天外套");

            assertThat(chatService.systemPrompt)
                    .contains("轻薄羽绒服")
                    .contains("¥299")
                    .contains("优衣库")
                    .contains("秋冬保暖");
        }

        @Test
        @DisplayName("无搜索结果时返回兜底回答，不调用 LLM")
        void ask_noProducts_returnsFallback() {
            esProductService.semanticResult = pageOf(List.of());

            RagResponse result = ragService.ask("不存在的商品");

            assertThat(result.answer()).contains("没有找到");
            assertThat(result.sourceProducts()).isEmpty();
            assertThat(chatService.chatCalls).isZero();
        }

        @Test
        @DisplayName("非购物问题直接引导回商品话题，不调用检索和 LLM")
        void ask_offTopic_returnsGuideWithoutSearch() {
            RagResponse result = ragService.ask("今天上海天气怎么样？");

            assertThat(result.answer()).contains("商城商品库");
            assertThat(result.sourceProducts()).isEmpty();
            assertThat(esProductService.semanticCalls).isZero();
            assertThat(chatService.chatCalls).isZero();
        }

        @Test
        @DisplayName("小米电视只应返回电视来源，不应混入手机")
        void ask_xiaomiTv_keepsTvOnly() {
            var tv = product("小米（MI）小米电视4A 55英寸", "小米", "电视", "HDR 4K超高清", "2499.00");
            var phone = product("小米8 全面屏游戏智能手机", "小米", "手机通讯", "骁龙845处理器", "2699.00");
            esProductService.semanticResult = pageOf(List.of(tv, phone));
            String question = "想买小米电视，预算4000以内";

            RagResponse result = ragService.ask(question);

            assertThat(result.sourceProducts()).hasSize(1);
            assertThat(result.sourceProducts()).extracting(EsProductResponse::name)
                    .containsExactly("小米（MI）小米电视4A 55英寸");
            assertThat(chatService.systemPrompt)
                    .contains("小米电视4A 55英寸")
                    .doesNotContain("小米8");
            assertThat(chatService.userMessage).isEqualTo(question);
        }

        @Test
        @DisplayName("小米笔记本只应返回笔记本来源，不应混入手机和电视")
        void ask_xiaomiLaptop_keepsLaptopOnly() {
            var laptop = product("小米 Xiaomi Book Pro 14 2022 锐龙版 2.8K超清大师屏 高端轻薄笔记本电脑", "小米", "笔记本", "2.8K超清大师屏", "5599.00");
            var tv = product("小米（MI）小米电视4A 55英寸", "小米", "电视", "HDR 4K超高清", "2499.00");
            var phone = product("小米8 全面屏游戏智能手机", "小米", "手机通讯", "骁龙845处理器", "2699.00");
            esProductService.semanticResult = pageOf(List.of(laptop, tv, phone));
            String question = "推荐一台小米笔记本，预算6000以内";

            RagResponse result = ragService.ask(question);

            assertThat(result.sourceProducts()).hasSize(1);
            assertThat(result.sourceProducts()).extracting(EsProductResponse::name)
                    .containsExactly("小米 Xiaomi Book Pro 14 2022 锐龙版 2.8K超清大师屏 高端轻薄笔记本电脑");
            assertThat(chatService.systemPrompt)
                    .contains("Xiaomi Book Pro 14 2022")
                    .doesNotContain("小米8")
                    .doesNotContain("小米电视4A");
            assertThat(chatService.userMessage).isEqualTo(question);
        }
    }

    @Nested
    @DisplayName("askStream - RAG 流式问答")
    class AskStreamTests {

        @Test
        @DisplayName("搜索到商品时先发送来源，再发送 token 和 complete")
        void askStream_withProducts_sendsSourcesTokensAndComplete() {
            var product = product("无线蓝牙耳机", "索尼", "耳机", "降噪耳机 续航30小时", "149.00");
            esProductService.semanticResult = pageOf(List.of(product));
            chatService.streamTokens = List.of("为您", "推荐", "索尼耳机");
            StreamCollector collector = new StreamCollector();

            ragService.askStream("推荐耳机", collector);

            assertThat(collector.events).containsExactly("sources", "token", "token", "token", "complete");
            assertThat(collector.sources).hasSize(1);
            assertThat(collector.sources.get(0).name()).isEqualTo("无线蓝牙耳机");
            assertThat(collector.tokens).containsExactly("为您", "推荐", "索尼耳机");
            assertThat(collector.complete.answer()).isEqualTo("为您推荐索尼耳机");
            assertThat(collector.complete.sourceProducts()).hasSize(1);
            assertThat(chatService.streamCalls).isEqualTo(1);
        }

        @Test
        @DisplayName("非购物问题流式返回引导消息，不调用检索和 LLM")
        void askStream_offTopic_returnsGuideWithoutSearch() {
            StreamCollector collector = new StreamCollector();

            ragService.askStream("今天上海天气怎么样？", collector);

            assertThat(collector.events).containsExactly("sources", "token", "complete");
            assertThat(collector.sources).isEmpty();
            assertThat(collector.tokens).singleElement().asString().contains("商城商品库");
            assertThat(collector.complete.answer()).contains("商城商品库");
            assertThat(esProductService.semanticCalls).isZero();
            assertThat(chatService.streamCalls).isZero();
        }
    }

    private static Page<EsProduct> pageOf(List<EsProduct> products) {
        return new PageImpl<>(products, PageRequest.of(0, 5), products.size());
    }

    private static EsProduct product(String name, String brandName, String categoryName, String subTitle, String price) {
        var product = new EsProduct();
        product.setName(name);
        product.setBrandName(brandName);
        product.setProductCategoryName(categoryName);
        product.setSubTitle(subTitle);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private static class FakeChatService implements ChatService {
        private String answer = "ok";
        private List<String> streamTokens = List.of("ok");
        private int chatCalls;
        private int streamCalls;
        private String systemPrompt;
        private String userMessage;

        @Override
        public String chat(String systemPrompt, String userMessage) {
            chatCalls++;
            this.systemPrompt = systemPrompt;
            this.userMessage = userMessage;
            return answer;
        }

        @Override
        public String streamChat(String systemPrompt, String userMessage, Consumer<String> tokenConsumer) {
            streamCalls++;
            this.systemPrompt = systemPrompt;
            this.userMessage = userMessage;
            streamTokens.forEach(tokenConsumer);
            return String.join("", streamTokens);
        }
    }

    private static class FakeEsProductService implements EsProductService {
        private Page<EsProduct> semanticResult = pageOf(List.of());
        private int semanticCalls;

        @Override
        public int importAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EsProduct create(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(List<Long> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId,
                                      Integer pageNum, Integer pageSize, Integer sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EsProductRelatedInfo searchRelatedInfo(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<EsProduct> searchSemantic(String keyword, Integer pageNum, Integer pageSize) {
            semanticCalls++;
            return semanticResult;
        }
    }

    private static class StreamCollector implements RagService.RagStreamSink {
        private final List<String> events = new ArrayList<>();
        private final List<String> tokens = new ArrayList<>();
        private List<EsProductResponse> sources = List.of();
        private RagResponse complete;

        @Override
        public void onSources(List<EsProductResponse> sourceProducts) {
            events.add("sources");
            sources = sourceProducts;
        }

        @Override
        public void onToken(String token) {
            events.add("token");
            tokens.add(token);
        }

        @Override
        public void onComplete(RagResponse response) {
            events.add("complete");
            complete = response;
        }
    }
}
