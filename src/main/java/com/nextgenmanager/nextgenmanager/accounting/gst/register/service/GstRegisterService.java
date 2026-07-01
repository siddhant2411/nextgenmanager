package com.nextgenmanager.nextgenmanager.accounting.gst.register.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.InwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterDto;

import java.time.LocalDate;

/**
 * GST registers — read projections over the source documents (no parallel tax store).
 * The General Ledger remains the source of truth; these registers tie to the same documents
 * that posted the Output/Input GST ledger movement for the period.
 */
public interface GstRegisterService {

    /** Outward supplies register: tax invoices + sales credit notes in the range. */
    OutwardRegisterDto outwardRegister(LocalDate from, LocalDate to);

    /** Inward supplies / ITC register: posted vendor invoices + debit notes in the range. */
    InwardRegisterDto inwardRegister(LocalDate from, LocalDate to);

    /** Outward register as an .xlsx workbook. */
    byte[] outwardExcel(OutwardRegisterDto register);

    /** Inward register as an .xlsx workbook. */
    byte[] inwardExcel(InwardRegisterDto register);
}
