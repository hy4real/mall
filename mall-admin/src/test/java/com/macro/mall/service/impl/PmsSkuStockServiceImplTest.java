package com.macro.mall.service.impl;

import com.macro.mall.dao.PmsSkuStockDao;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmsSkuStockServiceImpl 单元测试")
class PmsSkuStockServiceImplTest {

    @Mock private PmsSkuStockMapper skuStockMapper;
    @Mock private PmsSkuStockDao skuStockDao;

    @Captor private ArgumentCaptor<List<PmsSkuStock>> listCaptor;

    private PmsSkuStockServiceImpl skuStockService;

    @BeforeEach
    void setUp() {
        skuStockService = new PmsSkuStockServiceImpl(skuStockMapper, skuStockDao);
    }

    @Nested
    @DisplayName("getList - 根据商品ID查询SKU库存")
    class GetListTests {

        @Test
        @DisplayName("按 productId 查询")
        void getList_byProductId_delegates() {
            var expected = List.of(skuStock(1L, "SKU-001"), skuStock(1L, "SKU-002"));
            when(skuStockMapper.selectByExample(any(PmsSkuStockExample.class))).thenReturn(expected);

            var result = skuStockService.getList(1L, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("按 productId + keyword 模糊查询")
        void getList_withKeyword_filters() {
            when(skuStockMapper.selectByExample(any(PmsSkuStockExample.class))).thenReturn(Collections.emptyList());

            var result = skuStockService.getList(1L, "BLACK");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无匹配结果时返回空列表")
        void getList_noMatch_returnsEmpty() {
            when(skuStockMapper.selectByExample(any(PmsSkuStockExample.class))).thenReturn(List.of());

            var result = skuStockService.getList(999L, null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("update - 更新商品SKU库存")
    class UpdateTests {

        @Test
        @DisplayName("过滤出匹配 pid 的 SKU 后批量替换")
        void update_filtersByPidAndReplaces() {
            var s1 = skuStock(10L, "A");
            var s2 = skuStock(20L, "B"); // productId mismatch — should be filtered out
            var s3 = skuStock(10L, "C");
            var list = List.of(s1, s2, s3);
            when(skuStockDao.replaceList(anyList())).thenReturn(2);

            int count = skuStockService.update(10L, list);

            assertThat(count).isEqualTo(2);
            verify(skuStockDao).replaceList(listCaptor.capture());
            assertThat(listCaptor.getValue()).containsExactly(s1, s3);
        }

        @Test
        @DisplayName("全部不匹配时不调用 replaceList")
        void update_allMismatched_skipsReplace() {
            var list = List.of(skuStock(99L, "X"));
            when(skuStockDao.replaceList(anyList())).thenReturn(0);

            skuStockService.update(10L, list);

            verify(skuStockDao).replaceList(listCaptor.capture());
            assertThat(listCaptor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("空列表时 safe")
        void update_emptyList_safe() {
            when(skuStockDao.replaceList(anyList())).thenReturn(0);

            int count = skuStockService.update(10L, List.of());

            assertThat(count).isEqualTo(0);
            verify(skuStockDao).replaceList(listCaptor.capture());
            assertThat(listCaptor.getValue()).isEmpty();
        }
    }

    private static PmsSkuStock skuStock(Long productId, String skuCode) {
        var s = new PmsSkuStock();
        s.setProductId(productId);
        s.setSkuCode(skuCode);
        return s;
    }
}
