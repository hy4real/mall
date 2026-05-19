package com.macro.mall.searchmodern.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record OrderAnomaly(
        Long orderId,
        String orderSn,
        Long memberId,
        String memberUsername,
        LocalDateTime createTime,
        BigDecimal payAmount,
        BigDecimal totalAmount,
        Integer status,
        double score,
        String severity,
        List<String> reasonTags,
        List<String> reasons,
        Map<String, Object> features) {
}
