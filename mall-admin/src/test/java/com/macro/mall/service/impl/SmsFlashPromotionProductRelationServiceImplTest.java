package com.macro.mall.service.impl;

import com.macro.mall.dao.SmsFlashPromotionProductRelationDao;
import com.macro.mall.dto.SmsFlashPromotionProduct;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsFlashPromotionProductRelationServiceImpl 单元测试")
class SmsFlashPromotionProductRelationServiceImplTest {

    @Mock private SmsFlashPromotionProductRelationMapper relationMapper;
    @Mock private SmsFlashPromotionProductRelationDao relationDao;
    @Captor private ArgumentCaptor<SmsFlashPromotionProductRelation> captor;
    private SmsFlashPromotionProductRelationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsFlashPromotionProductRelationServiceImpl(relationMapper, relationDao);
    }

    @Nested
    @DisplayName("create - 批量创建关联")
    class CreateTests {
        @Test
        @DisplayName("逐条插入关联记录")
        void create_insertsEach() {
            var list = List.of(new SmsFlashPromotionProductRelation(), new SmsFlashPromotionProductRelation());
            when(relationMapper.insert(any())).thenReturn(1);

            int count = service.create(list);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("update - 更新关联")
    class UpdateTests {
        @Test
        @DisplayName("设置 ID 后更新")
        void update_setsId() {
            var relation = new SmsFlashPromotionProductRelation();
            when(relationMapper.updateByPrimaryKey(relation)).thenReturn(1);

            int count = service.update(3L, relation);

            assertThat(count).isEqualTo(1);
            assertThat(relation.getId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("delete - 删除关联")
    class DeleteTests {
        @Test
        @DisplayName("委托 mapper 删除")
        void delete_delegates() {
            when(relationMapper.deleteByPrimaryKey(5L)).thenReturn(1);

            assertThat(service.delete(5L)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getItem - 获取单个关联")
    class GetItemTests {
        @Test
        @DisplayName("委托 mapper 查询")
        void getItem_delegates() {
            var expected = new SmsFlashPromotionProductRelation();
            when(relationMapper.selectByPrimaryKey(1L)).thenReturn(expected);

            assertThat(service.getItem(1L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("list - 分页查询关联产品")
    class ListTests {
        @Test
        @DisplayName("委托 DAO 分页查询")
        void list_delegates() {
            var list = List.of(new SmsFlashPromotionProduct());
            when(relationDao.getList(1L, 2L)).thenReturn(list);

            assertThat(service.list(1L, 2L, 10, 1)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getCount - 查询关联数量")
    class GetCountTests {
        @Test
        @DisplayName("按活动和场次ID统计")
        void getCount_returnsCount() {
            when(relationMapper.countByExample(any(SmsFlashPromotionProductRelationExample.class))).thenReturn(3L);

            assertThat(service.getCount(1L, 2L)).isEqualTo(3L);
        }
    }
}
