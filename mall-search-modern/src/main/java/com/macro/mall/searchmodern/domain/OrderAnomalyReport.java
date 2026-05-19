package com.macro.mall.searchmodern.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record OrderAnomalyReport(
        Integer days,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        Integer totalOrders,
        Integer anomalyCount,
        List<OrderAnomaly> anomalies,
        Map<String, Long> summary) {
}
