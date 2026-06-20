package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

import java.math.BigDecimal;

/** A rate-wise tax line within a document (offline-tool {@code itm_det}). */
public record RateLine(
        BigDecimal rate,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal cess
) {}
