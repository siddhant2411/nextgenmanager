package com.nextgenmanager.nextgenmanager.accounting.gst.register.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One outward (sales) document in the GST register. {@code docType} is INV (tax invoice) or
 * CRN (credit note); credit-note amounts are positive here and netted in returns.
 */
public record OutwardRegisterRow(
        String docType,
        String docNo,
        LocalDate docDate,
        String gstin,
        String partyName,
        String placeOfSupply,
        BigDecimal taxableValue,
        BigDecimal rate,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal cess,
        BigDecimal total
) {}
