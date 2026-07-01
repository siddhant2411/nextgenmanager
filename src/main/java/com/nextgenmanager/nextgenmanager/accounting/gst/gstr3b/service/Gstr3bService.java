package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.Gstr3bDto;

import java.time.LocalDate;

/** Builds GSTR-3B from the registers and reconciles it to the Output/Input GST ledgers. */
public interface Gstr3bService {

    Gstr3bDto build(LocalDate from, LocalDate to);

    byte[] toExcel(Gstr3bDto gstr3b);

    /** GSTN offline-tool JSON (UTF-8 bytes). */
    byte[] toJson(Gstr3bDto gstr3b);
}
