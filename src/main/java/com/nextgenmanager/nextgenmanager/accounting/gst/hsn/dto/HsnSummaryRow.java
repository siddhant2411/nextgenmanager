package com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto;

import java.math.BigDecimal;

/**
 * One HSN/SAC + rate line of the GSTR-1 Table 12 summary, net of credit notes.
 * {@code uqc} is the unit quantity code (item UOM).
 */
public record HsnSummaryRow(
        String hsnCode,
        String description,
        String uqc,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal cess,
        BigDecimal totalValue
) {}
