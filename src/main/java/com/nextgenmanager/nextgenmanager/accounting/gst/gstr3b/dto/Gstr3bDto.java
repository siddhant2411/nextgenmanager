package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GSTR-3B summary return, structured to match the official form sections.
 *
 * <pre>
 *  3.1(a) outwardTaxable  — forward-charge invoices and credit notes (net)
 *  3.1(d) inwardRcm       — reverse-charge inward supplies
 *  3.2    section32        — inter-state supplies to unregistered/composition/UIN holders
 *  4A     itcAvailable     — eligible ITC from vendor bills (always ≥ 0)
 *  4B     itcReversed      — ITC reversed via debit notes (always ≥ 0)
 *  4C     netItcAvailable  — 4A − 4B, floored at zero per head
 *  4D     itcIneligible    — blocked credits (Sec 17(5) etc.)
 *  6      netTaxPayable    — 3.1(a) tax − 4C, floored at zero per head
 * </pre>
 */
public record Gstr3bDto(
        String gstin,
        LocalDate from,
        LocalDate to,
        TaxAmounts outwardTaxable,    // 3.1(a)
        TaxAmounts inwardRcm,         // 3.1(d)
        List<Section32Row> section32, // 3.2
        TaxAmounts itcAvailable,      // 4A — bills only, always ≥ 0
        TaxAmounts itcReversed,       // 4B — debit notes, always ≥ 0
        TaxAmounts netItcAvailable,   // 4C = 4A − 4B floored
        TaxAmounts itcIneligible,     // 4D
        TaxAmounts netTaxPayable,     // 6 = 3.1(a) tax − 4C floored
        GstReconciliation reconciliation
) {}
