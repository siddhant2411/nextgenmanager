package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GSTR-1 B2B invoice (registered recipient). One entry per invoice, rate-wise lines inside. */
public record B2bInvoice(
        String ctin,            // recipient GSTIN
        String partyName,
        String invoiceNo,
        LocalDate invoiceDate,
        BigDecimal invoiceValue,
        String placeOfSupply,
        boolean reverseCharge,
        List<RateLine> items
) {}
