package com.macro.mall.searchmodern.service;

import com.macro.mall.searchmodern.domain.OrderAnomalyReport;

public interface OrderAnomalyService {
    OrderAnomalyReport detectOrderAnomalies(Integer days);
}
