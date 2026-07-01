package com.nextgenmanager.nextgenmanager.accounting.gst.register.dto;

import java.time.LocalDate;
import java.util.List;

/** Inward supplies / ITC register for a date range: line-itemised rows plus column totals. */
public record InwardRegisterDto(
        LocalDate from,
        LocalDate to,
        List<InwardRegisterRow> rows,
        GstTotals totals
) {}
