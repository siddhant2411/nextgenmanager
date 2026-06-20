package com.nextgenmanager.nextgenmanager.accounting.gst.register.dto;

import java.time.LocalDate;
import java.util.List;

/** Outward supplies register for a date range: line-itemised rows plus column totals. */
public record OutwardRegisterDto(
        LocalDate from,
        LocalDate to,
        List<OutwardRegisterRow> rows,
        GstTotals totals
) {}
