package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GSTR-1 outward-supplies return for a period. Sections mirror the GSTN offline tool. EXP/SEZ
 * tables are deferred (domestic first — locked decision #5) so are not present here yet.
 */
public record Gstr1Dto(
        String gstin,           // company GSTIN
        LocalDate from,
        LocalDate to,
        List<B2bInvoice> b2b,
        List<B2clInvoice> b2cl,
        List<B2csRow> b2cs,
        List<CdnRow> cdnr,      // credit notes to registered parties
        List<CdnRow> cdnur,     // credit notes to unregistered parties
        HsnSummaryDto hsn,
        List<DocSeries> docs,
        Gstr1Totals totals
) {
    /** Headline totals across all sections (for the preview banner and reconciliation). */
    public record Gstr1Totals(
            BigDecimal taxableValue,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal cess,
            int documentCount
    ) {}
}
