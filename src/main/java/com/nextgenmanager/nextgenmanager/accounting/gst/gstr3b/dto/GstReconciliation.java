package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto;

import java.math.BigDecimal;

/**
 * The CA contract: GSTR-3B must tie to the General Ledger. {@code ledgerOutput} is the Output-GST
 * (9010–9013) net-credit movement for the period; {@code returnOutput} is the 3B outward tax.
 * {@code ledgerItc} is the Input-GST (6020–6023) net-debit movement; {@code returnItc} is the 3B
 * eligible ITC. {@code tiesOut} is true when both variances are within rounding tolerance.
 */
public record GstReconciliation(
        BigDecimal ledgerOutput,
        BigDecimal returnOutput,
        BigDecimal outputVariance,
        BigDecimal ledgerItc,
        BigDecimal returnItc,
        BigDecimal itcVariance,
        boolean tiesOut
) {}
