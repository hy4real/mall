package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPortalOrderServiceImpl 单元测试")
class OmsPortalOrderServiceImplTest {

    private UmsMemberService memberService;
    private OmsCartItemService cartItemService;
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    private UmsMemberCouponService memberCouponService;
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    private SmsCouponHistoryDao couponHistoryDao;
    private OmsOrderMapper orderMapper;
    private PortalOrderItemDao orderItemDao;
    private SmsCouponHistoryMapper couponHistoryMapper;
    private RedisService redisService;
    private PortalOrderDao portalOrderDao;
    private OmsOrderSettingMapper orderSettingMapper;
    private OmsOrderItemMapper orderItemMapper;
    private AmqpTemplate amqpTemplate;
    private CancelOrderSender cancelOrderSender;
    private OmsPortalOrderServiceImpl service;

    private final UmsMember member = createMember(1L);

    @BeforeEach
    void setUp() {
        memberService = mock(UmsMemberService.class);
        cartItemService = mock(OmsCartItemService.class);
        memberReceiveAddressService = mock(UmsMemberReceiveAddressService.class);
        memberCouponService = mock(UmsMemberCouponService.class);
        integrationConsumeSettingMapper = mock(UmsIntegrationConsumeSettingMapper.class);
        couponHistoryDao = mock(SmsCouponHistoryDao.class);
        orderMapper = mock(OmsOrderMapper.class);
        orderItemDao = mock(PortalOrderItemDao.class);
        couponHistoryMapper = mock(SmsCouponHistoryMapper.class);
        redisService = mock(RedisService.class);
        portalOrderDao = mock(PortalOrderDao.class);
        orderSettingMapper = mock(OmsOrderSettingMapper.class);
        orderItemMapper = mock(OmsOrderItemMapper.class);
        amqpTemplate = mock(AmqpTemplate.class);
        cancelOrderSender = new CancelOrderSender(amqpTemplate);
        service = new OmsPortalOrderServiceImpl(memberService, cartItemService, memberReceiveAddressService,
                memberCouponService, integrationConsumeSettingMapper, couponHistoryDao, orderMapper, orderItemDao,
                couponHistoryMapper, redisService, portalOrderDao, orderSettingMapper, orderItemMapper, cancelOrderSender);
        ReflectionTestUtils.setField(service, "REDIS_KEY_ORDER_ID", "oms:orderId");
        ReflectionTestUtils.setField(service, "REDIS_DATABASE", "mall");
    }

    @Nested
    @DisplayName("generateConfirmOrder 方法")
    class GenerateConfirmOrder {

        @Test
        @DisplayName("聚合确认单信息")
        void generateConfirmOrder_returnsAggregatedResult() {
            when(memberService.getCurrentMember()).thenReturn(member);
            CartPromotionItem cartItem = buildCartPromotionItem(1L, new BigDecimal("100.00"), 1);
            when(cartItemService.listPromotion(1L, List.of(1L))).thenReturn(List.of(cartItem));
            when(memberReceiveAddressService.list()).thenReturn(List.of());
            when(memberCouponService.listCart(any(), eq(1))).thenReturn(List.of());
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(new UmsIntegrationConsumeSetting());

            ConfirmOrderResult result = service.generateConfirmOrder(List.of(1L));

            assertThat(result).isNotNull();
            assertThat(result.getCartPromotionItemList()).hasSize(1);
            assertThat(result.getCalcAmount()).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateOrder 方法")
    class GenerateOrder {

        @Test
        @DisplayName("未选择收货地址时抛异常")
        void generateOrder_noAddress_throws() {
            OrderParam param = new OrderParam(null, null, 0, 0, List.of(1L));

            assertThatThrownBy(() -> service.generateOrder(param))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("库存不足时抛异常")
        void generateOrder_noStock_throws() {
            when(memberService.getCurrentMember()).thenReturn(member);
            CartPromotionItem cartItem = buildCartPromotionItem(1L, new BigDecimal("100.00"), 99);
            cartItem.setRealStock(0);
            when(cartItemService.listPromotion(1L, List.of(1L))).thenReturn(List.of(cartItem));

            OrderParam param = new OrderParam(1L, null, 0, 0, List.of(1L));

            assertThatThrownBy(() -> service.generateOrder(param))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("正常下单返回 GenerateOrderResult")
        void generateOrder_valid_returnsResult() {
            when(memberService.getCurrentMember()).thenReturn(member);
            CartPromotionItem cartItem = buildCartPromotionItem(1L, new BigDecimal("100.00"), 1);
            when(cartItemService.listPromotion(1L, List.of(1L))).thenReturn(List.of(cartItem));

            UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
            address.setName("张三");
            address.setPhoneNumber("13800000000");
            when(memberReceiveAddressService.getItem(1L)).thenReturn(address);

            when(portalOrderDao.lockStockBySkuId(anyLong(), anyInt())).thenReturn(1);
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);
            when(orderMapper.insert(any())).thenReturn(1);
            when(orderItemDao.insertList(any())).thenReturn(1);
            OmsOrderSetting orderSetting = new OmsOrderSetting();
            orderSetting.setNormalOrderOvertime(60);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(orderSetting);
            when(orderSettingMapper.selectByExample(any())).thenReturn(List.of());

            // Mock TransactionSynchronizationManager — 需要活跃事务
            // 由于测试中没有事务上下文，TransactionSynchronizationManager.registerSynchronization 会抛异常
            // 所以我们用 lenient 来避免严格模式下的未使用 mock 问题

            OrderParam param = new OrderParam(1L, null, 0, 0, List.of(1L));

            GenerateOrderResult result = service.generateOrder(param);
            assertThat(result).isNotNull();
            assertThat(result.order()).isNotNull();
            assertThat(result.orderItemList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("paySuccess 方法")
    class PaySuccess {

        @Test
        @DisplayName("先扣库存再改状态，扣库存失败时抛异常")
        void paySuccess_stockDeductionFails_throws() {
            OmsOrderDetail detail = new OmsOrderDetail();
            OmsOrderItem item = new OmsOrderItem();
            item.setProductSkuId(1L);
            item.setProductQuantity(2);
            detail.setOrderItemList(List.of(item));
            when(portalOrderDao.getDetail(1L)).thenReturn(detail);
            when(portalOrderDao.reduceSkuStock(1L, 2)).thenReturn(0);

            assertThatThrownBy(() -> service.paySuccess(1L, 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("库存扣减成功后更新订单状态为已支付")
        void paySuccess_stockDeducted_updatesStatus() {
            OmsOrderDetail detail = new OmsOrderDetail();
            OmsOrderItem item = new OmsOrderItem();
            item.setProductSkuId(1L);
            item.setProductQuantity(2);
            detail.setOrderItemList(List.of(item));
            when(portalOrderDao.getDetail(1L)).thenReturn(detail);
            when(portalOrderDao.reduceSkuStock(1L, 2)).thenReturn(1);
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            Integer result = service.paySuccess(1L, 1);

            assertThat(result).isEqualTo(1);
            verify(orderMapper).updateByExampleSelective(argThat(order ->
                    order.getStatus() == 1), any());
        }
    }

    @Nested
    @DisplayName("cancelOrder 方法")
    class CancelOrder {

        @Test
        @DisplayName("原子操作：状态非 0 时跳过（updated=0）")
        void cancelOrder_notStatus0_skips() {
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(0);

            service.cancelOrder(1L);

            verify(orderItemMapper, never()).selectByExample(any());
        }

        @Test
        @DisplayName("取消订单后释放库存")
        void cancelOrder_status0_releasesStock() {
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductSkuId(10L);
            orderItem.setProductQuantity(3);
            when(orderItemMapper.selectByExample(any())).thenReturn(List.of(orderItem));
            when(portalOrderDao.releaseStockBySkuId(10L, 3)).thenReturn(1);

            OmsOrder order = new OmsOrder();
            order.setId(1L);
            order.setMemberId(1L);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);

            service.cancelOrder(1L);

            verify(portalOrderDao).releaseStockBySkuId(10L, 3);
            verify(couponHistoryMapper, never()).updateByPrimaryKeySelective(any());
        }

        @Test
        @DisplayName("取消有优惠券的订单时恢复优惠券状态")
        void cancelOrder_withCoupon_restoresCoupon() {
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductSkuId(10L);
            orderItem.setProductQuantity(1);
            when(orderItemMapper.selectByExample(any())).thenReturn(List.of(orderItem));
            when(portalOrderDao.releaseStockBySkuId(10L, 1)).thenReturn(1);

            OmsOrder order = new OmsOrder();
            order.setId(1L);
            order.setMemberId(1L);
            order.setCouponId(100L);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);

            service.cancelOrder(1L);

            verify(couponHistoryMapper).selectByExample(any());
        }
    }

    @Nested
    @DisplayName("confirmReceiveOrder 方法")
    class ConfirmReceiveOrder {

        @Test
        @DisplayName("非本人订单时抛异常")
        void confirmReceiveOrder_notOwner_throws() {
            when(memberService.getCurrentMember()).thenReturn(member);
            OmsOrder order = new OmsOrder();
            order.setMemberId(999L);
            order.setStatus(2);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);

            assertThatThrownBy(() -> service.confirmReceiveOrder(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("他人订单");
        }

        @Test
        @DisplayName("订单未发货时抛异常")
        void confirmReceiveOrder_notShipped_throws() {
            when(memberService.getCurrentMember()).thenReturn(member);
            OmsOrder order = new OmsOrder();
            order.setMemberId(1L);
            order.setStatus(1);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);

            assertThatThrownBy(() -> service.confirmReceiveOrder(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("未发货");
        }

        @Test
        @DisplayName("确认收货使用条件更新 WHERE status=2 AND memberId")
        void confirmReceiveOrder_valid_updatesWithCondition() {
            when(memberService.getCurrentMember()).thenReturn(member);
            OmsOrder order = new OmsOrder();
            order.setMemberId(1L);
            order.setStatus(2);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            service.confirmReceiveOrder(1L);

            verify(orderMapper).updateByExampleSelective(
                    argThat(o -> o.getStatus() == 3 && o.getConfirmStatus() == 1),
                    any());
        }
    }

    @Nested
    @DisplayName("deleteOrder 方法")
    class DeleteOrder {

        @Test
        @DisplayName("非本人订单时抛异常")
        void deleteOrder_notOwner_throws() {
            when(memberService.getCurrentMember()).thenReturn(member);
            OmsOrder order = new OmsOrder();
            order.setMemberId(999L);
            order.setStatus(3);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);

            assertThatThrownBy(() -> service.deleteOrder(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("他人订单");
        }

        @Test
        @DisplayName("订单状态非已完成/已关闭时抛异常")
        void deleteOrder_invalidStatus_throws() {
            when(memberService.getCurrentMember()).thenReturn(member);
            OmsOrder order = new OmsOrder();
            order.setMemberId(1L);
            order.setStatus(0);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);

            assertThatThrownBy(() -> service.deleteOrder(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("已完成或已关闭");
        }

        @Test
        @DisplayName("正常删除使用条件更新")
        void deleteOrder_valid_updates() {
            when(memberService.getCurrentMember()).thenReturn(member);
            OmsOrder order = new OmsOrder();
            order.setMemberId(1L);
            order.setStatus(3);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            service.deleteOrder(1L);

            verify(orderMapper).updateByExampleSelective(
                    argThat(o -> o.getDeleteStatus() == 1),
                    any());
        }
    }

    @Nested
    @DisplayName("sendDelayMessageCancelOrder 方法")
    class SendDelayMessage {

        @Test
        @DisplayName("根据订单设置发送延迟消息")
        void sendDelayMessage_sendsWithCorrectDelay() {
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setNormalOrderOvertime(60);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            service.sendDelayMessageCancelOrder(1L);

            verify(amqpTemplate).convertAndSend(
                    anyString(),
                    anyString(),
                    eq(1L),
                    any());
        }
    }

    @Nested
    @DisplayName("paySuccessByOrderSn 方法")
    class PaySuccessByOrderSn {

        @Test
        @DisplayName("根据 orderSn 找到订单后调用 paySuccess")
        void paySuccessByOrderSn_found_callsPaySuccess() {
            OmsOrder order = new OmsOrder();
            order.setId(1L);
            when(orderMapper.selectByExample(any())).thenReturn(List.of(order));

            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderItemList(List.of());
            when(portalOrderDao.getDetail(1L)).thenReturn(detail);
            when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            service.paySuccessByOrderSn("SN001", 1);

            verify(orderMapper).updateByExampleSelective(any(), any());
        }

        @Test
        @DisplayName("orderSn 不存在时不操作")
        void paySuccessByOrderSn_notFound_noop() {
            when(orderMapper.selectByExample(any())).thenReturn(List.of());

            service.paySuccessByOrderSn("SN999", 1);

            verify(portalOrderDao, never()).getDetail(anyLong());
        }
    }

    @Nested
    @DisplayName("list 方法")
    class ListOrders {

        @Test
        @DisplayName("status=-1 查询所有订单")
        void list_statusMinus1_queriesAll() {
            when(memberService.getCurrentMember()).thenReturn(member);
            when(orderMapper.selectByExample(any())).thenReturn(java.util.List.of());

            com.macro.mall.common.api.CommonPage<OmsOrderDetail> result = service.list(-1, 1, 10);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("detail 方法")
    class Detail {

        @Test
        @DisplayName("查询订单详情含商品列表")
        void detail_returnsOrderWithItems() {
            OmsOrder order = new OmsOrder();
            order.setId(1L);
            when(orderMapper.selectByPrimaryKey(1L)).thenReturn(order);
            when(orderItemMapper.selectByExample(any())).thenReturn(List.of(new OmsOrderItem()));

            OmsOrderDetail result = service.detail(1L);

            assertThat(result.getOrderItemList()).hasSize(1);
        }
    }

    // --- Helper Methods ---

    private UmsMember createMember(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        m.setNickname("testUser");
        m.setUsername("testuser");
        m.setIntegration(1000);
        return m;
    }

    private CartPromotionItem buildCartPromotionItem(Long productId, BigDecimal price, int quantity) {
        CartPromotionItem item = new CartPromotionItem();
        item.setId(1L);
        item.setProductId(productId);
        item.setProductSkuId(10L);
        item.setProductName("商品A");
        item.setProductPic("pic.jpg");
        item.setProductAttr("颜色：红色");
        item.setProductBrand("品牌");
        item.setProductSn("SN001");
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setProductSkuCode("SKU001");
        item.setProductCategoryId(1L);
        item.setReduceAmount(BigDecimal.ZERO);
        item.setPromotionMessage("无优惠");
        item.setIntegration(10);
        item.setGrowth(20);
        item.setRealStock(100);
        return item;
    }
}
