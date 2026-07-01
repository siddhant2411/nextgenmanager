package com.nextgenmanager.nextgenmanager.accounting.gst.register.dto;

import java.math.BigDecimal;

/** Column-wise totals shared by the outward and inward GST registers. */
public record GstTotals(
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal cess,
        BigDecimal total
) {
    public static GstTotals zero() {
        return new GstTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public GstTotals add(BigDecimal taxable, BigDecimal c, BigDecimal s, BigDecimal i, BigDecimal cs, BigDecimal t) {
        return new GstTotals(
                taxableValue.add(nz(taxable)), cgst.add(nz(c)), sgst.add(nz(s)),
                igst.add(nz(i)), cess.add(nz(cs)), total.add(nz(t)));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
