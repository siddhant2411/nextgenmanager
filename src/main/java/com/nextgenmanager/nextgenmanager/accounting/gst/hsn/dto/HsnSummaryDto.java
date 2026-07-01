package com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto;

import java.time.LocalDate;
import java.util.List;

/** HSN/SAC summary of outward supplies for a date range (rows + a TOTAL row). */
public record HsnSummaryDto(
        LocalDate from,
        LocalDate to,
        List<HsnSummaryRow> rows,
        HsnSummaryRow totals
) {}
