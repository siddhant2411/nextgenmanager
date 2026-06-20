package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GSTR-1 B2C-Large invoice: inter-state, unregistered recipient, invoice value &gt; ₹2,50,000. */
public record B2clInvoice(
        String invoiceNo,
        LocalDate invoiceDate,
        BigDecimal invoiceValue,
        String placeOfSupply,
        String partyName,
        List<RateLine> items
) {}
