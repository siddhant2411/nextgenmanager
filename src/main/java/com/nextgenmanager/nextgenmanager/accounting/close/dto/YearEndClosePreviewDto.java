package com.nextgenmanager.nextgenmanager.accounting.close.dto;

import com.nextgenmanager.nextgenmanager.accounting.period.model.FyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What closing a financial year would do: the checks it must pass and the CLOSING voucher it would
 * post — every income and expense ledger's balance at year-end reversed, the net to Retained Earnings.
 */
@Data
@NoArgsConstructor
public class YearEndClosePreviewDto {
    private Long financialYearId;
    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    private FyStatus status;

    private List<Check> checks = new ArrayList<>();
    /** True when every check passes (and the year is open). */
    private boolean canClose;

    private BigDecimal totalIncome = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    /** Income − expenses: credited to Retained Earnings (debited when negative). */
    private BigDecimal netProfit = BigDecimal.ZERO;
    private String retainedEarningsCode;
    private String retainedEarningsName;
    private List<Line> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Check {
        private String code;
        private String label;
        private boolean passed;
        private String detail;
    }

    /** One ledger the closing voucher clears. {@code balance} is signed for its nature (income Cr − Dr, expense Dr − Cr). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long accountId;
        private String code;
        private String name;
        private String nature;
        private BigDecimal balance;
        private BigDecimal closingDr;
        private BigDecimal closingCr;
    }
}
