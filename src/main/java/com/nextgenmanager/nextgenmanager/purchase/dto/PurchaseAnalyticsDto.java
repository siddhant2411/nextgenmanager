package com.nextgenmanager.nextgenmanager.purchase.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PurchaseAnalyticsDto(
        Map<String, BigDecimal> spendByVendor,
        Map<String, Long> statusCounts,
        List<PurchaseOrderListDto> overduePOs,
        List<MonthlySpendDto> monthlySpend
) {
    public record MonthlySpendDto(String yearMonth, BigDecimal total) {}
}
