package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One row of a financial statement: an account group (with children) or a ledger (a leaf).
 * Amounts are already signed for the side they sit on — an asset or expense is Dr − Cr, a
 * liability, equity or income line is Cr − Dr — so a negative figure is a contra balance
 * (e.g. Accumulated Depreciation under Fixed Assets, a customer in advance under Trade Receivables).
 */
@Data
@NoArgsConstructor
public class StatementLineDto {

    public static final String KIND_GROUP = "GROUP";
    public static final String KIND_LEDGER = "LEDGER";
    /** A computed line that is not a ledger, e.g. the profit not yet closed into Retained Earnings. */
    public static final String KIND_COMPUTED = "COMPUTED";

    private String kind;
    private String code;
    private String name;
    private Long accountId;          // set on ledgers only — the UI drills into the ledger statement
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal previousAmount = BigDecimal.ZERO;
    private List<StatementLineDto> children = new ArrayList<>();

    public StatementLineDto(String kind, String code, String name) {
        this.kind = kind;
        this.code = code;
        this.name = name;
    }
}
