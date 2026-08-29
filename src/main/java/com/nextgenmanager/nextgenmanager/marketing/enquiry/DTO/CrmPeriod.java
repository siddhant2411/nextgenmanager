package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * The reporting window every CRM figure is measured over, plus the window it is compared against.
 *
 * <h3>Why this type exists</h3>
 * {@code getEnquirySummary()} used to take no arguments and every counting query behind it was
 * unbounded. A dashboard that cannot answer "this month versus last month" is a counter, not a
 * dashboard, and no trend, cohort or run-rate figure is computable without a window.
 *
 * <h3>Flow versus stock — the distinction this type does NOT apply to everything</h3>
 * A period bounds <em>flow</em> metrics: things that happened inside the window (leads created,
 * enquiries won, revenue booked). It must never be applied to <em>stock</em> metrics: the state of
 * the desk right now (open pipeline, overdue follow-ups, enquiries never contacted). Filtering
 * "overdue follow-ups" to a month produces a number that means nothing and shrinks at the start of
 * every month, which reads as improvement. See {@link EnquirySummaryDTO} for how the two are kept
 * apart in the response.
 *
 * <h3>Bounds are always concrete</h3>
 * {@link #from} and {@link #to} are never null, including for ALL_TIME, which widens to sentinel
 * bounds instead. Callers and SQL therefore never need a null branch, which is what turned the old
 * date-comparator handling into a filter that silently matched nothing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrmPeriod {

    /** Widest sensible bounds. ALL_TIME uses these so the SQL has one code path, not two. */
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX = LocalDate.of(9999, 12, 31);

    public static final String THIS_MONTH   = "THIS_MONTH";
    public static final String LAST_MONTH   = "LAST_MONTH";
    public static final String THIS_QUARTER = "THIS_QUARTER";
    public static final String THIS_FY      = "THIS_FY";
    public static final String LAST_FY      = "LAST_FY";
    public static final String LAST_30_DAYS = "LAST_30_DAYS";
    public static final String LAST_90_DAYS = "LAST_90_DAYS";
    public static final String CUSTOM       = "CUSTOM";
    public static final String ALL_TIME     = "ALL_TIME";

    public static final Set<String> PRESETS = Set.of(
            THIS_MONTH, LAST_MONTH, THIS_QUARTER, THIS_FY, LAST_FY,
            LAST_30_DAYS, LAST_90_DAYS, CUSTOM, ALL_TIME);

    /** Which preset produced these bounds. Echoed back so the UI can keep its control in step. */
    private String preset;

    private LocalDate from;
    private LocalDate to;

    /** Human label for the window, e.g. "Aug 2026" or "FY 2026-27". Rendered as-is by the UI. */
    private String label;

    // ------------------------------------------------------------------ construction

    /**
     * Resolves the window a request asked for.
     *
     * <p>Explicit {@code from}/{@code to} always win over a preset — if a caller sends both, the
     * dates are what they meant. Sending neither yields {@link #THIS_MONTH}, which is a deliberate
     * behaviour change from the old unbounded summary: an all-time figure under a label that says
     * nothing about its period is the bug this whole type exists to remove. Callers that genuinely
     * want every row must now ask for {@link #ALL_TIME} by name.
     */
    public static CrmPeriod resolve(String preset, LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            LocalDate f = from != null ? from : MIN;
            LocalDate t = to != null ? to : LocalDate.now();
            if (f.isAfter(t)) {
                throw new InvalidDataException("from (" + f + ") is after to (" + t + ")");
            }
            return new CrmPeriod(CUSTOM, f, t, describe(f, t));
        }

        String p = (preset == null || preset.isBlank()) ? THIS_MONTH : preset.trim().toUpperCase();
        if (!PRESETS.contains(p)) {
            throw new InvalidDataException("preset must be one of " + PRESETS + " but was '" + preset + "'");
        }

        LocalDate today = LocalDate.now();
        switch (p) {
            case THIS_MONTH: {
                LocalDate f = today.withDayOfMonth(1);
                return new CrmPeriod(p, f, f.plusMonths(1).minusDays(1), monthLabel(f));
            }
            case LAST_MONTH: {
                LocalDate f = today.withDayOfMonth(1).minusMonths(1);
                return new CrmPeriod(p, f, f.plusMonths(1).minusDays(1), monthLabel(f));
            }
            case THIS_QUARTER: {
                LocalDate f = today.withDayOfMonth(1).minusMonths((today.getMonthValue() - 1) % 3);
                LocalDate t = f.plusMonths(3).minusDays(1);
                return new CrmPeriod(p, f, t, describe(f, t));
            }
            case THIS_FY: {
                LocalDate f = fyStart(today);
                return new CrmPeriod(p, f, f.plusYears(1).minusDays(1), fyLabel(f));
            }
            case LAST_FY: {
                LocalDate f = fyStart(today).minusYears(1);
                return new CrmPeriod(p, f, f.plusYears(1).minusDays(1), fyLabel(f));
            }
            case LAST_30_DAYS: {
                LocalDate f = today.minusDays(29);
                return new CrmPeriod(p, f, today, "Last 30 days");
            }
            case LAST_90_DAYS: {
                LocalDate f = today.minusDays(89);
                return new CrmPeriod(p, f, today, "Last 90 days");
            }
            case ALL_TIME:
            default:
                return new CrmPeriod(ALL_TIME, MIN, MAX, "All time");
        }
    }

    /**
     * The window immediately before this one, of equal length — the comparison every delta on the
     * dashboard is measured against.
     *
     * <p>Deliberately <em>not</em> "the same period last year". Year-on-year is the right
     * comparison for a mature dataset with real seasonality; on a register that is one year old it
     * compares against a period that barely has data, and every delta reads as spectacular growth.
     * Equal-length-immediately-preceding is the honest default.
     *
     * <p>Null for ALL_TIME: there is nothing before all of time, and rendering a delta against a
     * fabricated window would be worse than rendering none.
     */
    @JsonIgnore
    public CrmPeriod previous() {
        if (isAllTime()) return null;

        // Calendar-aligned presets step back by their own unit so February compares against
        // January rather than against "the 28 days before February", which would overlap January.
        LocalDate f;
        LocalDate t;
        switch (preset == null ? CUSTOM : preset) {
            case THIS_MONTH:
            case LAST_MONTH:
                f = from.minusMonths(1);
                t = f.plusMonths(1).minusDays(1);
                break;
            case THIS_QUARTER:
                f = from.minusMonths(3);
                t = f.plusMonths(3).minusDays(1);
                break;
            case THIS_FY:
            case LAST_FY:
                f = from.minusYears(1);
                t = f.plusYears(1).minusDays(1);
                break;
            default: {
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                t = from.minusDays(1);
                f = t.minusDays(days - 1);
            }
        }
        return new CrmPeriod(preset, f, t, describe(f, t));
    }

    @JsonIgnore
    public boolean isAllTime() {
        return ALL_TIME.equals(preset);
    }

    // ------------------------------------------------------------------ Indian financial year

    /**
     * April 1 to March 31.
     *
     * <p>Computed arithmetically rather than read from the accounting module's {@code FinancialYear}
     * table on purpose: that table is configuration a tenant may not have set up, and the CRM
     * dashboard must work on day one whether or not anybody has opened the books. If the two ever
     * need to agree on a non-standard year, this is the single place to change.
     */
    private static LocalDate fyStart(LocalDate on) {
        int year = on.getMonthValue() >= 4 ? on.getYear() : on.getYear() - 1;
        return LocalDate.of(year, 4, 1);
    }

    private static String fyLabel(LocalDate fyStart) {
        int y = fyStart.getYear();
        return String.format("FY %d-%02d", y, (y + 1) % 100);
    }

    private static String monthLabel(LocalDate d) {
        return d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                + " " + d.getYear();
    }

    private static String describe(LocalDate f, LocalDate t) {
        if (MIN.equals(f) && MAX.equals(t)) return "All time";
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);
        return f.format(fmt) + " – " + t.format(fmt);
    }
}
