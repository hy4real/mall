package com.macro.mall.portal.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.portal.config.AlipayConfig;
import com.macro.mall.portal.domain.AliPayParam;
import com.macro.mall.portal.service.OmsPortalOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlipayServiceImpl 单元测试")
class AlipayServiceImplTest {

    private AlipayConfig alipayConfig;
    private AlipayClient alipayClient;
    private OmsOrderMapper orderMapper;
    private OmsPortalOrderService portalOrderService;
    private AlipayServiceImpl service;

    @BeforeEach
    void setUp() {
        alipayConfig = mock(AlipayConfig.class);
        alipayClient = mock(AlipayClient.class);
        orderMapper = mock(OmsOrderMapper.class);
        portalOrderService = mock(OmsPortalOrderService.class);
        service = new AlipayServiceImpl(alipayConfig, alipayClient, orderMapper, portalOrderService);
    }

    @Nested
    @DisplayName("pay 方法")
    class Pay {

        @Test
        @DisplayName("PC 网站支付返回 HTML 表单")
        void pay_returnsFormHtml() throws AlipayApiException {
            AliPayParam param = new AliPayParam("SN001", "商品", new BigDecimal("99.00"));
            when(alipayConfig.getNotifyUrl()).thenReturn("http://notify.url");
            when(alipayConfig.getReturnUrl()).thenReturn("http://return.url");

            AlipayTradePagePayResponse mockResponse = mock(AlipayTradePagePayResponse.class);
            when(mockResponse.getBody()).thenReturn("<form>pay</form>");
            when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(mockResponse);

            String result = service.pay(param);

            assertThat(result).isEqualTo("<form>pay</form>");
        }

        @Test
        @DisplayName("AlipayApiException 时返回 null")
        void pay_apiException_returnsNull() throws AlipayApiException {
            AliPayParam param = new AliPayParam("SN001", "商品", new BigDecimal("99.00"));
            when(alipayConfig.getNotifyUrl()).thenReturn(null);
            when(alipayConfig.getReturnUrl()).thenReturn(null);
            when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class)))
                    .thenThrow(new AlipayApiException("error"));

            String result = service.pay(param);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("notify 方法")
    class Notify {

        @Test
        @DisplayName("签名校验失败时返回 failure")
        void notify_invalidSignature_returnsFailure() {
            when(alipayConfig.getAlipayPublicKey()).thenReturn("pubkey");
            when(alipayConfig.getCharset()).thenReturn("UTF-8");
            when(alipayConfig.getSignType()).thenReturn("RSA2");

            Map<String, String> params = new java.util.HashMap<>(Map.of("trade_status", "TRADE_SUCCESS", "out_trade_no", "SN001"));
            String result = service.notify(params);

            assertThat(result).isEqualTo("failure");
            verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("webPay 方法")
    class WebPay {

        @Test
        @DisplayName("手机网站支付使用 QUICK_WAP_WAY")
        void webPay_usesWapProductCode() throws AlipayApiException {
            AliPayParam param = new AliPayParam("SN001", "商品", new BigDecimal("99.00"));
            when(alipayConfig.getNotifyUrl()).thenReturn(null);
            when(alipayConfig.getReturnUrl()).thenReturn(null);

            com.alipay.api.response.AlipayTradeWapPayResponse mockResponse = mock(com.alipay.api.response.AlipayTradeWapPayResponse.class);
            when(mockResponse.getBody()).thenReturn("<form>wap</form>");
            when(alipayClient.pageExecute(any(com.alipay.api.request.AlipayTradeWapPayRequest.class))).thenReturn(mockResponse);

            String result = service.webPay(param);

            assertThat(result).isEqualTo("<form>wap</form>");
        }
    }
}
