package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

import java.math.BigDecimal;

/** GSTR-1 B2C-Small: consolidated outward supplies to unregistered, by place-of-supply + rate. */
public record B2csRow(
        String type,            // "OE" (other than e-commerce)
        String placeOfSupply,
        BigDecimal rate,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal cess
) {}
