package com.macro.mall.service.impl;

import com.macro.mall.dao.SmsCouponDao;
import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsCouponServiceImpl 单元测试")
class SmsCouponServiceImplTest {

    @Mock private SmsCouponMapper couponMapper;
    @Mock private SmsCouponProductRelationMapper productRelationMapper;
    @Mock private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;
    @Mock private SmsCouponProductRelationDao productRelationDao;
    @Mock private SmsCouponProductCategoryRelationDao productCategoryRelationDao;
    @Mock private SmsCouponDao couponDao;

    @Captor private ArgumentCaptor<SmsCoupon> couponCaptor;

    private SmsCouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        couponService = new SmsCouponServiceImpl(
                couponMapper, productRelationMapper, productCategoryRelationMapper,
                productRelationDao, productCategoryRelationDao, couponDao);
    }

    private SmsCouponParam makeParam(Integer useType) {
        var param = new SmsCouponParam();
        param.setName("测试优惠券");
        param.setType(0);
        param.setPlatform(0);
        param.setAmount(new BigDecimal("10.00"));
        param.setPerLimit(1);
        param.setMinPoint(new BigDecimal("50.00"));
        param.setPublishCount(100);
        param.setUseType(useType);
        return param;
    }

    @Nested
    @DisplayName("create - 创建优惠券")
    class CreateTests {

        @Test
        @DisplayName("创建全场通用券时不插入关联关系")
        void create_globalCoupon_insertsOnlyCoupon() {
            var param = makeParam(0);
            when(couponMapper.insert(param)).thenReturn(1);

            int count = couponService.create(param);

            assertThat(count).isEqualTo(1);
            assertThat(param.getCount()).isEqualTo(100);
            assertThat(param.getUseCount()).isEqualTo(0);
            assertThat(param.getReceiveCount()).isEqualTo(0);
            verify(productRelationDao, never()).insertList(anyList());
            verify(productCategoryRelationDao, never()).insertList(anyList());
        }

        @Test
        @DisplayName("创建指定分类券时插入分类关联关系")
        void create_categoryCoupon_insertsCategoryRelations() {
            var param = makeParam(1);
            var relations = List.of(
                    categoryRelation(1L), categoryRelation(2L));
            param.setProductCategoryRelationList(relations);
            when(couponMapper.insert(param)).thenAnswer(inv -> {
                param.setId(10L);
                return 1;
            });

            couponService.create(param);

            verify(productCategoryRelationDao).insertList(relations);
            assertThat(relations.get(0).getCouponId()).isEqualTo(10L);
            assertThat(relations.get(1).getCouponId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("创建指定商品券时插入商品关联关系")
        void create_productCoupon_insertsProductRelations() {
            var param = makeParam(2);
            var relations = List.of(
                    productRelation(1L), productRelation(2L));
            param.setProductRelationList(relations);
            when(couponMapper.insert(param)).thenAnswer(inv -> {
                param.setId(20L);
                return 1;
            });

            couponService.create(param);

            verify(productRelationDao).insertList(relations);
            assertThat(relations.get(0).getCouponId()).isEqualTo(20L);
            assertThat(relations.get(1).getCouponId()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("delete - 删除优惠券")
    class DeleteTests {

        @Test
        @DisplayName("删除优惠券并级联删除关联关系")
        void delete_cascadesToRelations() {
            when(couponMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            int count = couponService.delete(5L);

            assertThat(count).isEqualTo(1);
            verify(productRelationMapper).deleteByExample(any());
            verify(productCategoryRelationMapper).deleteByExample(any());
        }
    }

    @Nested
    @DisplayName("update - 更新优惠券")
    class UpdateTests {

        @Test
        @DisplayName("更新全场通用券时不处理关联关系")
        void update_globalCoupon_noRelationChange() {
            var param = makeParam(0);
            when(couponMapper.updateByPrimaryKey(param)).thenReturn(1);

            int count = couponService.update(3L, param);

            assertThat(count).isEqualTo(1);
            assertThat(param.getId()).isEqualTo(3L);
            verify(productRelationDao, never()).insertList(anyList());
            verify(productCategoryRelationDao, never()).insertList(anyList());
        }

        @Test
        @DisplayName("更新指定分类券时替换分类关联关系")
        void update_categoryCoupon_replacesCategoryRelations() {
            var param = makeParam(1);
            param.setId(3L);
            var relations = List.of(categoryRelation(3L));
            param.setProductCategoryRelationList(relations);
            when(couponMapper.updateByPrimaryKey(param)).thenReturn(1);

            couponService.update(3L, param);

            verify(productCategoryRelationMapper).deleteByExample(any());
            verify(productCategoryRelationDao).insertList(relations);
            assertThat(relations.get(0).getCouponId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("更新指定商品券时替换商品关联关系")
        void update_productCoupon_replacesProductRelations() {
            var param = makeParam(2);
            param.setId(3L);
            var relations = List.of(productRelation(4L));
            param.setProductRelationList(relations);
            when(couponMapper.updateByPrimaryKey(param)).thenReturn(1);

            couponService.update(3L, param);

            verify(productRelationMapper).deleteByExample(any());
            verify(productRelationDao).insertList(relations);
            assertThat(relations.get(0).getCouponId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("list - 优惠券列表查询")
    class ListTests {

        @Test
        @DisplayName("根据名称模糊查询")
        void list_withName_filters() {
            var expected = List.of(new SmsCoupon());
            when(couponMapper.selectByExample(any())).thenReturn(expected);

            var result = couponService.list("测试", null, 10, 1);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("根据类型精确查询")
        void list_withType_filters() {
            var expected = List.of(new SmsCoupon(), new SmsCoupon());
            when(couponMapper.selectByExample(any())).thenReturn(expected);

            var result = couponService.list(null, 1, 10, 1);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("无过滤条件时返回全部")
        void list_noFilters_returnsAll() {
            var expected = List.of(new SmsCoupon());
            when(couponMapper.selectByExample(any())).thenReturn(expected);

            var result = couponService.list(null, null, 5, 1);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getItem - 获取优惠券详情")
    class GetItemTests {

        @Test
        @DisplayName("委托 couponDao 查询")
        void getItem_delegates() {
            var expected = new SmsCouponParam();
            when(couponDao.getItem(7L)).thenReturn(expected);

            var result = couponService.getItem(7L);

            assertThat(result).isSameAs(expected);
        }
    }

    private static SmsCouponProductRelation productRelation(Long productId) {
        var r = new SmsCouponProductRelation();
        r.setProductId(productId);
        return r;
    }

    private static SmsCouponProductCategoryRelation categoryRelation(Long categoryId) {
        var r = new SmsCouponProductCategoryRelation();
        r.setProductCategoryId(categoryId);
        return r;
    }
}
