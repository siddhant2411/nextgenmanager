package com.nextgenmanager.nextgenmanager.accounting.gst.register.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One inward (purchase) document in the GST register. {@code docType} is BILL (vendor invoice) or
 * DBN (debit note). {@code itcEligible} and {@code reverseCharge} drive GSTR-3B section 4 / 3.1(d).
 */
public record InwardRegisterRow(
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
        BigDecimal total,
        boolean itcEligible,
        boolean reverseCharge
) {}
