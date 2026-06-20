package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto;

import java.math.BigDecimal;

/** A taxable-value + tax-head tuple used throughout the GSTR-3B sections. */
public record TaxAmounts(
        BigDecimal taxableValue,
        BigDecimal igst,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal cess
) {
    public static TaxAmounts zero() {
        return new TaxAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public TaxAmounts add(BigDecimal taxable, BigDecimal i, BigDecimal c, BigDecimal s, BigDecimal cs) {
        return new TaxAmounts(taxableValue.add(nz(taxable)), igst.add(nz(i)), cgst.add(nz(c)),
                sgst.add(nz(s)), cess.add(nz(cs)));
    }

    public TaxAmounts subtract(BigDecimal taxable, BigDecimal i, BigDecimal c, BigDecimal s, BigDecimal cs) {
        return new TaxAmounts(taxableValue.subtract(nz(taxable)), igst.subtract(nz(i)), cgst.subtract(nz(c)),
                sgst.subtract(nz(s)), cess.subtract(nz(cs)));
    }

    /** Total tax across all heads (excludes taxable value). */
    public BigDecimal totalTax() {
        return igst.add(cgst).add(sgst).add(cess);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
