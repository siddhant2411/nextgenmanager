package com.nextgenmanager.nextgenmanager.accounting.tds.util;

import java.time.LocalDate;

/** Derives the Indian financial year and TDS quarter for a date (FY starts 1 April). */
public final class TdsPeriods {

    private TdsPeriods() {}

    /** e.g. 2025-06-09 → "2025-26"; 2026-02-10 → "2025-26". */
    public static String financialYear(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        int endYY = (startYear + 1) % 100;
        return startYear + "-" + String.format("%02d", endYY);
    }

    /** Q1 Apr-Jun, Q2 Jul-Sep, Q3 Oct-Dec, Q4 Jan-Mar. */
    public static String quarter(LocalDate date) {
        int m = date.getMonthValue();
        if (m >= 4 && m <= 6) return "Q1";
        if (m >= 7 && m <= 9) return "Q2";
        if (m >= 10 && m <= 12) return "Q3";
        return "Q4";
    }
}
