package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.RagResponse;
import com.macro.mall.searchmodern.service.ChatService;
import com.macro.mall.searchmodern.service.EsProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagServiceImpl 单元测试")
class RagServiceImplTest {

    @Mock private EsProductService esProductService;
    @Mock private ChatService chatService;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
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
            Page<EsProduct> page = new PageImpl<>(List.of(product), PageRequest.of(0, 5), 1);
            when(esProductService.searchSemantic("推荐耳机", 0, 5)).thenReturn(page);
            when(chatService.chat(anyString(), eq("推荐耳机")))
                    .thenReturn("为您推荐索尼无线蓝牙耳机，售价149元");

            RagResponse result = ragService.ask("推荐耳机");

            assertThat(result.answer()).contains("索尼");
            assertThat(result.sourceProducts()).hasSize(1);
            assertThat(result.sourceProducts().get(0).name()).isEqualTo("无线蓝牙耳机");
            verify(chatService).chat(anyString(), eq("推荐耳机"));
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
            Page<EsProduct> page = new PageImpl<>(List.of(product), PageRequest.of(0, 5), 1);
            when(esProductService.searchSemantic(anyString(), anyInt(), anyInt())).thenReturn(page);
            when(chatService.chat(anyString(), anyString())).thenReturn("ok");

            ragService.ask("冬天外套");

            verify(chatService).chat(
                    org.mockito.ArgumentMatchers.argThat(sys -> sys.contains("轻薄羽绒服")
                            && sys.contains("¥299")
                            && sys.contains("优衣库")
                            && sys.contains("秋冬保暖")),
                    anyString());
        }

        @Test
        @DisplayName("无搜索结果时返回兜底回答，不调用 LLM")
        void ask_noProducts_returnsFallback() {
            Page<EsProduct> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 5), 0);
            when(esProductService.searchSemantic("不存在的商品", 0, 5)).thenReturn(emptyPage);

            RagResponse result = ragService.ask("不存在的商品");

            assertThat(result.answer()).contains("没有找到");
            assertThat(result.sourceProducts()).isEmpty();
        }

        @Test
        @DisplayName("非购物问题直接引导回商品话题，不调用检索和 LLM")
        void ask_offTopic_returnsGuideWithoutSearch() {
            RagResponse result = ragService.ask("今天上海天气怎么样？");

            assertThat(result.answer()).contains("商城商品库");
            assertThat(result.sourceProducts()).isEmpty();
            verify(esProductService, never()).searchSemantic(anyString(), anyInt(), anyInt());
            verify(chatService, never()).chat(anyString(), anyString());
        }

        @Test
        @DisplayName("小米电视只应返回电视来源，不应混入手机")
        void ask_xiaomiTv_keepsTvOnly() {
            var tv = product("小米（MI）小米电视4A 55英寸", "小米", "电视", "HDR 4K超高清", "2499.00");
            var phone = product("小米8 全面屏游戏智能手机", "小米", "手机通讯", "骁龙845处理器", "2699.00");
            Page<EsProduct> page = new PageImpl<>(List.of(tv, phone), PageRequest.of(0, 5), 2);
            String question = "想买小米电视，预算4000以内";
            when(esProductService.searchSemantic(question, 0, 5)).thenReturn(page);
            when(chatService.chat(anyString(), eq(question))).thenReturn("ok");

            RagResponse result = ragService.ask(question);

            assertThat(result.sourceProducts()).hasSize(1);
            assertThat(result.sourceProducts()).extracting(EsProductResponse::name)
                    .containsExactly("小米（MI）小米电视4A 55英寸");
            verify(chatService).chat(
                    org.mockito.ArgumentMatchers.argThat(sys -> sys.contains("小米电视4A 55英寸")
                            && !sys.contains("小米8")),
                    eq(question));
        }

        @Test
        @DisplayName("小米笔记本只应返回笔记本来源，不应混入手机和电视")
        void ask_xiaomiLaptop_keepsLaptopOnly() {
            var laptop = product("小米 Xiaomi Book Pro 14 2022 锐龙版 2.8K超清大师屏 高端轻薄笔记本电脑", "小米", "笔记本", "2.8K超清大师屏", "5599.00");
            var tv = product("小米（MI）小米电视4A 55英寸", "小米", "电视", "HDR 4K超高清", "2499.00");
            var phone = product("小米8 全面屏游戏智能手机", "小米", "手机通讯", "骁龙845处理器", "2699.00");
            Page<EsProduct> page = new PageImpl<>(List.of(laptop, tv, phone), PageRequest.of(0, 5), 3);
            String question = "推荐一台小米笔记本，预算6000以内";
            when(esProductService.searchSemantic(question, 0, 5)).thenReturn(page);
            when(chatService.chat(anyString(), eq(question))).thenReturn("ok");

            RagResponse result = ragService.ask(question);

            assertThat(result.sourceProducts()).hasSize(1);
            assertThat(result.sourceProducts()).extracting(EsProductResponse::name)
                    .containsExactly("小米 Xiaomi Book Pro 14 2022 锐龙版 2.8K超清大师屏 高端轻薄笔记本电脑");
            verify(chatService).chat(
                    org.mockito.ArgumentMatchers.argThat(sys -> sys.contains("Xiaomi Book Pro 14 2022")
                            && !sys.contains("小米8")
                            && !sys.contains("小米电视4A")),
                    eq(question));
        }
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
}
