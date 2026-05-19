package com.macro.mall.searchmodern.dao;

import com.macro.mall.searchmodern.domain.OrderInteraction;
import com.macro.mall.searchmodern.domain.OrderSignal;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AiAnalysisDao {
    List<OrderInteraction> listOrderInteractions();

    List<OrderSignal> listOrdersInAnalysisWindow(@Param("days") Integer days);

    LocalDateTime getOrderAnalysisWindowEnd();
}
