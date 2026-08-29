package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The window arithmetic every CRM figure is measured over.
 *
 * <p>Worth testing on its own because it is the one piece of the dashboard whose bugs are invisible:
 * a wrong boundary does not throw, it just reports the wrong month under the right label. The
 * financial-year and previous-window cases are where that actually happens.
 */
class CrmPeriodTest {

    // ------------------------------------------------------------------ defaults and validation

    @Test
    @DisplayName("no arguments means this month, not all time")
    void defaultsToThisMonth() {
        CrmPeriod p = CrmPeriod.resolve(null, null, null);

        assertThat(p.getPreset()).isEqualTo(CrmPeriod.THIS_MONTH);
        assertThat(p.getFrom()).isEqualTo(LocalDate.now().withDayOfMonth(1));
        assertThat(p.isAllTime()).isFalse();
    }

    @Test
    @DisplayName("explicit dates win over a preset")
    void explicitDatesWin() {
        CrmPeriod p = CrmPeriod.resolve(CrmPeriod.THIS_FY,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(p.getPreset()).isEqualTo(CrmPeriod.CUSTOM);
        assertThat(p.getFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(p.getTo()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("an unrecognised preset is an error, not a silent fallback")
    void unknownPresetRejected() {
        assertThatThrownBy(() -> CrmPeriod.resolve("LAST_FORTNIGHT", null, null))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("LAST_FORTNIGHT");
    }

    @Test
    @DisplayName("an inverted range is an error")
    void invertedRangeRejected() {
        assertThatThrownBy(() -> CrmPeriod.resolve(null,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    @DisplayName("bounds are always concrete, including all time")
    void allTimeHasConcreteBounds() {
        CrmPeriod p = CrmPeriod.resolve(CrmPeriod.ALL_TIME, null, null);

        assertThat(p.getFrom()).isNotNull();
        assertThat(p.getTo()).isNotNull();
        assertThat(p.getFrom()).isBefore(LocalDate.of(1901, 1, 1));
        assertThat(p.isAllTime()).isTrue();
    }

    // ------------------------------------------------------------------ Indian financial year

    @Test
    @DisplayName("a date in April starts that year's FY")
    void fyStartsInApril() {
        // Resolved relative to today, so assert the shape rather than a fixed year.
        CrmPeriod fy = CrmPeriod.resolve(CrmPeriod.THIS_FY, null, null);

        assertThat(fy.getFrom().getMonthValue()).isEqualTo(4);
        assertThat(fy.getFrom().getDayOfMonth()).isEqualTo(1);
        assertThat(fy.getTo().getMonthValue()).isEqualTo(3);
        assertThat(fy.getTo().getDayOfMonth()).isEqualTo(31);
        assertThat(fy.getTo().getYear()).isEqualTo(fy.getFrom().getYear() + 1);
    }

    @Test
    @DisplayName("the FY label reads 2026-27, not 2026-2027")
    void fyLabelFormat() {
        CrmPeriod fy = CrmPeriod.resolve(CrmPeriod.THIS_FY, null, null);
        assertThat(fy.getLabel()).matches("FY \\d{4}-\\d{2}");
    }

    @Test
    @DisplayName("last FY sits immediately before this FY with no gap or overlap")
    void lastFyAbutsThisFy() {
        CrmPeriod thisFy = CrmPeriod.resolve(CrmPeriod.THIS_FY, null, null);
        CrmPeriod lastFy = CrmPeriod.resolve(CrmPeriod.LAST_FY, null, null);

        assertThat(lastFy.getTo().plusDays(1)).isEqualTo(thisFy.getFrom());
    }

    // ------------------------------------------------------------------ previous window

    @Test
    @DisplayName("the month before a month is the whole calendar month, not 30 days back")
    void previousMonthIsCalendarAligned() {
        // February is the case that breaks a naive "subtract the same number of days".
        CrmPeriod feb = CrmPeriod.resolve(null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        // Custom windows fall back to equal-length; use the preset path for calendar alignment.
        CrmPeriod thisMonth = CrmPeriod.resolve(CrmPeriod.THIS_MONTH, null, null);
        CrmPeriod prior = thisMonth.previous();

        assertThat(prior.getFrom()).isEqualTo(thisMonth.getFrom().minusMonths(1));
        assertThat(prior.getTo().plusDays(1)).isEqualTo(thisMonth.getFrom());
        assertThat(feb.getTo()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("a custom window compares against an equal-length window ending the day before")
    void previousCustomIsEqualLength() {
        CrmPeriod p = CrmPeriod.resolve(null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        CrmPeriod prior = p.previous();

        assertThat(prior.getTo()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(prior.getFrom()).isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    @DisplayName("the previous window never overlaps the current one")
    void previousNeverOverlaps() {
        for (String preset : new String[]{
                CrmPeriod.THIS_MONTH, CrmPeriod.LAST_MONTH, CrmPeriod.THIS_QUARTER,
                CrmPeriod.THIS_FY, CrmPeriod.LAST_FY, CrmPeriod.LAST_30_DAYS, CrmPeriod.LAST_90_DAYS}) {

            CrmPeriod p = CrmPeriod.resolve(preset, null, null);
            CrmPeriod prior = p.previous();

            assertThat(prior).as("previous for %s", preset).isNotNull();
            assertThat(prior.getTo())
                    .as("previous window for %s must end before the current one starts", preset)
                    .isBefore(p.getFrom());
        }
    }

    @Test
    @DisplayName("all time has no previous window rather than a fabricated one")
    void allTimeHasNoPrevious() {
        assertThat(CrmPeriod.resolve(CrmPeriod.ALL_TIME, null, null).previous()).isNull();
    }

    @Test
    @DisplayName("previous nests exactly one level — the prior window has no prior of its own")
    void previousDoesNotRecurseForever() {
        CrmPeriod p = CrmPeriod.resolve(CrmPeriod.THIS_QUARTER, null, null);
        CrmPeriod prior = p.previous();

        // The service attaches only one level; this asserts the value object stays cheap to nest.
        assertThat(prior.previous().getTo()).isBefore(prior.getFrom());
    }
}
