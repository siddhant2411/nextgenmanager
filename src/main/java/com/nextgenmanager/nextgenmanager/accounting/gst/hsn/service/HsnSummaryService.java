package com.nextgenmanager.nextgenmanager.accounting.gst.hsn.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryDto;

import java.time.LocalDate;

/** HSN/SAC summary of outward supplies (GSTR-1 Table 12), aggregated from invoice + credit-note lines. */
public interface HsnSummaryService {

    HsnSummaryDto summary(LocalDate from, LocalDate to);

    byte[] toExcel(HsnSummaryDto summary);
}
