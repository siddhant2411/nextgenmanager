package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GSTR-1 credit/debit note row (CDNR registered, CDNUR unregistered). For our books these are
 * sales credit notes only. {@code noteType} is "C" (credit). {@code ctin} is null for CDNUR.
 */
public record CdnRow(
        String ctin,
        String partyName,
        String noteNo,
        LocalDate noteDate,
        String noteType,        // "C" credit / "D" debit
        BigDecimal noteValue,
        String placeOfSupply,
        List<RateLine> items
) {}
