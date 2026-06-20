package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

/**
 * GSTR-1 Documents-issued series (Table 13): the first and last document number of a series and
 * how many were issued. {@code cancelled} is 0 here (cancellations aren't gap-tracked yet).
 */
public record DocSeries(
        String nature,          // e.g. "Tax Invoices", "Credit Notes"
        String fromNo,
        String toNo,
        int totalCount,
        int cancelled
) {}
