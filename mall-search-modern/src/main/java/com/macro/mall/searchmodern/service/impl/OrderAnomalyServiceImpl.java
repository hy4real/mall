package com.macro.mall.searchmodern.service.impl;

import com.macro.mall.searchmodern.dao.AiAnalysisDao;
import com.macro.mall.searchmodern.domain.OrderAnomaly;
import com.macro.mall.searchmodern.domain.OrderAnomalyReport;
import com.macro.mall.searchmodern.domain.OrderSignal;
import com.macro.mall.searchmodern.service.OrderAnomalyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderAnomalyServiceImpl implements OrderAnomalyService {
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 3650;
    private static final int ORDER_BURST_WARNING_THRESHOLD = 5;
    private static final int ORDER_BURST_CRITICAL_THRESHOLD = 10;
    private static final double HIGH_RETURN_RATE_THRESHOLD = 0.5;
    private static final int MIN_ORDERS_FOR_RETURN_RATE = 3;

    private final AiAnalysisDao aiAnalysisDao;

    public OrderAnomalyServiceImpl(AiAnalysisDao aiAnalysisDao) {
        this.aiAnalysisDao = aiAnalysisDao;
    }

    @Override
    public OrderAnomalyReport detectOrderAnomalies(Integer days) {
        int safeDays = safeDays(days);
        List<OrderSignal> orders = aiAnalysisDao.listOrdersInAnalysisWindow(safeDays);
        if (orders.isEmpty()) {
            return new OrderAnomalyReport(safeDays, null, aiAnalysisDao.getOrderAnalysisWindowEnd(),
                    0, 0, List.of(), Map.of());
        }

        AmountStats globalAmountStats = AmountStats.from(orders, this::amountOf);
        Map<Long, List<OrderSignal>> ordersByMember = orders.stream()
                .filter(order -> order.getMemberId() != null)
                .collect(Collectors.groupingBy(OrderSignal::getMemberId));
        Map<Long, MemberStats> memberStats = ordersByMember.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> MemberStats.from(entry.getValue())));

        List<OrderAnomaly> anomalies = orders.stream()
                .map(order -> detectOrder(order, ordersByMember, memberStats, globalAmountStats))
                .filter(anomaly -> !anomaly.reasonTags().isEmpty())
                .sorted(Comparator
                        .comparingDouble(OrderAnomaly::score).reversed()
                        .thenComparing(OrderAnomaly::createTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Long> summary = anomalies.stream()
                .flatMap(anomaly -> anomaly.reasonTags().stream())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        LocalDateTime windowStart = orders.stream()
                .map(OrderSignal::getCreateTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime windowEnd = orders.stream()
                .map(OrderSignal::getCreateTime)
                .max(Comparator.naturalOrder())
                .orElse(aiAnalysisDao.getOrderAnalysisWindowEnd());

        return new OrderAnomalyReport(safeDays, windowStart, windowEnd,
                orders.size(), anomalies.size(), anomalies, summary);
    }

    private OrderAnomaly detectOrder(OrderSignal order,
                                     Map<Long, List<OrderSignal>> ordersByMember,
                                     Map<Long, MemberStats> memberStats,
                                     AmountStats globalAmountStats) {
        Set<String> reasonTags = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();
        Map<String, Object> features = new LinkedHashMap<>();
        double score = 0.0;

        List<OrderSignal> memberOrders = ordersByMember.getOrDefault(order.getMemberId(), List.of());
        int rollingHourCount = rollingHourCount(order, memberOrders);
        features.put("rollingHourOrderCount", rollingHourCount);
        if (rollingHourCount >= ORDER_BURST_CRITICAL_THRESHOLD) {
            score += 4.0;
            add(reasonTags, reasons, "ORDER_BURST", "同一会员 1 小时内下单超过 10 次");
        } else if (rollingHourCount >= ORDER_BURST_WARNING_THRESHOLD) {
            score += 2.5;
            add(reasonTags, reasons, "ORDER_BURST", "同一会员 1 小时内下单达到高频阈值");
        }

        MemberStats stats = memberStats.get(order.getMemberId());
        if (stats != null) {
            features.put("memberOrderCount", stats.orderCount());
            features.put("memberClosedRate", stats.closedRate());
            if (stats.orderCount() >= MIN_ORDERS_FOR_RETURN_RATE
                    && stats.closedRate() > HIGH_RETURN_RATE_THRESHOLD
                    && isClosed(order)) {
                score += 2.5;
                add(reasonTags, reasons, "HIGH_RETURN_RATE", "该会员关闭/退货类订单占比超过 50%");
            }
        }

        BigDecimal amount = amountOf(order);
        if (amount != null) {
            double zScore = globalAmountStats.zScore(amount.doubleValue());
            features.put("amountZScore", Math.round(zScore * 100.0) / 100.0);
            if (zScore >= 2.5) {
                score += 3.0;
                add(reasonTags, reasons, "AMOUNT_OUTLIER", "订单金额显著高于整体订单均值");
            } else if (zScore >= 1.5 && amount.subtract(globalAmountStats.averageAsBigDecimal()).compareTo(new BigDecimal("1000")) > 0) {
                score += 2.0;
                add(reasonTags, reasons, "AMOUNT_OUTLIER", "订单金额高于近期订单常规区间");
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                score += 3.0;
                add(reasonTags, reasons, "INVALID_AMOUNT", "订单金额小于或等于 0");
            }
        }

        if (order.getPayAmount() != null && order.getTotalAmount() != null
                && order.getPayAmount().subtract(order.getTotalAmount()).compareTo(BigDecimal.ONE) > 0) {
            score += 2.0;
            add(reasonTags, reasons, "PAY_AMOUNT_GT_TOTAL", "应付金额大于订单总金额");
        }

        if (order.getCreateTime() != null) {
            int hour = order.getCreateTime().getHour();
            features.put("orderHour", hour);
            if (hour >= 0 && hour < 5) {
                score += 1.5;
                add(reasonTags, reasons, "ODD_HOUR", "订单发生在凌晨低活跃时段");
            }
        }

        if (order.getStatus() != null && order.getStatus() == 5) {
            score += 4.0;
            add(reasonTags, reasons, "INVALID_ORDER_STATUS", "订单状态为无效订单");
        }

        return new OrderAnomaly(
                order.getId(),
                order.getOrderSn(),
                order.getMemberId(),
                order.getMemberUsername(),
                order.getCreateTime(),
                order.getPayAmount(),
                order.getTotalAmount(),
                order.getStatus(),
                Math.round(score * 100.0) / 100.0,
                severity(score),
                List.copyOf(reasonTags),
                List.copyOf(reasons),
                features);
    }

    private static void add(Set<String> reasonTags, List<String> reasons, String tag, String reason) {
        if (reasonTags.add(tag)) {
            reasons.add(reason);
        }
    }

    private static int rollingHourCount(OrderSignal order, List<OrderSignal> memberOrders) {
        if (order.getCreateTime() == null) {
            return 0;
        }
        LocalDateTime start = order.getCreateTime().minusHours(1);
        LocalDateTime end = order.getCreateTime().plusHours(1);
        int count = 0;
        for (OrderSignal memberOrder : memberOrders) {
            LocalDateTime createTime = memberOrder.getCreateTime();
            if (createTime != null && !createTime.isBefore(start) && !createTime.isAfter(end)) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal amountOf(OrderSignal order) {
        if (order.getPayAmount() != null) {
            return order.getPayAmount();
        }
        return order.getTotalAmount();
    }

    private static boolean isClosed(OrderSignal order) {
        return order.getStatus() != null && order.getStatus() == 4;
    }

    private static String severity(double score) {
        if (score >= 5.0) {
            return "HIGH";
        }
        if (score >= 3.0) {
            return "MEDIUM";
        }
        if (score > 0.0) {
            return "LOW";
        }
        return "NONE";
    }

    private static int safeDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private record MemberStats(int orderCount, double closedRate) {
        private static MemberStats from(List<OrderSignal> orders) {
            long closed = orders.stream().filter(OrderAnomalyServiceImpl::isClosed).count();
            return new MemberStats(orders.size(), orders.isEmpty() ? 0.0 : closed * 1.0 / orders.size());
        }
    }

    private record AmountStats(double average, double stdDev) {
        private static AmountStats from(List<OrderSignal> orders, Function<OrderSignal, BigDecimal> amountExtractor) {
            List<Double> amounts = orders.stream()
                    .map(amountExtractor)
                    .filter(amount -> amount != null)
                    .map(BigDecimal::doubleValue)
                    .toList();
            if (amounts.isEmpty()) {
                return new AmountStats(0.0, 0.0);
            }
            double average = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = amounts.stream()
                    .mapToDouble(amount -> Math.pow(amount - average, 2))
                    .average()
                    .orElse(0.0);
            return new AmountStats(average, Math.sqrt(variance));
        }

        private double zScore(double amount) {
            if (stdDev <= 0.0) {
                return 0.0;
            }
            return (amount - average) / stdDev;
        }

        private BigDecimal averageAsBigDecimal() {
            return BigDecimal.valueOf(average);
        }
    }
}
