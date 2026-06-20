package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto.Gstr1Dto;

import java.time.LocalDate;

/** Builds the GSTR-1 outward-supplies return and renders it as Excel / offline-tool JSON. */
public interface Gstr1Service {

    Gstr1Dto build(LocalDate from, LocalDate to);

    byte[] toExcel(Gstr1Dto gstr1);

    /** GSTN offline-tool JSON (UTF-8 bytes). */
    byte[] toJson(Gstr1Dto gstr1);
}
