package com.nextgenmanager.nextgenmanager.accounting.gst.filing.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.filing.dto.GstReturnDto;

import java.util.List;

/**
 * Files GST returns: persists an immutable snapshot of the computed return. Filing GSTR-3B locks
 * the period; GSTR-1 is recorded but does not hard-lock (locked decision #2). Re-filing GSTR-3B
 * requires the period to be unlocked first (ROLE_ACCOUNTS_HEAD).
 */
public interface GstReturnService {

    GstReturnDto fileGstr1(Long periodId, String user);

    GstReturnDto fileGstr3b(Long periodId, String user);

    /** Filed returns for a financial year (without the JSON payload). */
    List<GstReturnDto> getFilings(Long financialYearId);

    /** A single filed return including its JSON payload snapshot. */
    GstReturnDto getReturn(Long id);
}
