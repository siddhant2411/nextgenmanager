package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkCalendarTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);
    private static final Set<DayOfWeek> MON_TO_SAT = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);

    private static WorkCalendar dayShift() {
        return new WorkCalendar()
                .shift(LocalTime.of(9, 0), LocalTime.of(17, 0), MON_TO_SAT, 1.0)
                .weeklyOff(DayOfWeek.SUNDAY);
    }

    @Test
    void aWorkingDayIsOneWindowPerShift() {
        List<TimeWindow> windows = dayShift().windowsOn(MONDAY);

        assertThat(windows).containsExactly(
                new TimeWindow(MONDAY.atTime(9, 0), MONDAY.atTime(17, 0), 1.0));
    }

    @Test
    void aBreakLowersTheRateInsteadOfShorteningTheShift() {
        WorkCalendar calendar = new WorkCalendar()
                .shift(LocalTime.of(9, 0), LocalTime.of(17, 0), MON_TO_SAT, 450.0 / 480.0);

        assertThat(calendar.windowsOn(MONDAY).get(0).wallMinutes()).isEqualTo(480);
        assertThat(calendar.productiveMinutesOn(MONDAY)).isEqualTo(450);
    }

    @Test
    void weeklyOffDayAndFullHolidayAreClosed() {
        WorkCalendar calendar = dayShift().holiday(MONDAY);

        assertThat(calendar.windowsOn(SUNDAY)).isEmpty();
        assertThat(calendar.windowsOn(MONDAY)).isEmpty();
    }

    @Test
    void partialHolidayCutsItsHoursOutOfTheShift() {
        WorkCalendar calendar = dayShift().partialHoliday(MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0));

        assertThat(calendar.windowsOn(MONDAY)).containsExactly(
                new TimeWindow(MONDAY.atTime(9, 0), MONDAY.atTime(12, 0), 1.0),
                new TimeWindow(MONDAY.atTime(14, 0), MONDAY.atTime(17, 0), 1.0));
    }

    @Test
    void overrideOpensAnOffDayAndClosesAWorkingOne() {
        WorkCalendar calendar = dayShift().forceWorking(SUNDAY).forceOff(MONDAY);

        // No shift lists Sunday, so the working Sunday runs the normal shifts.
        assertThat(calendar.productiveMinutesOn(SUNDAY)).isEqualTo(480);
        assertThat(calendar.windowsOn(MONDAY)).isEmpty();
    }

    @Test
    void nightShiftRunsIntoTheNextDay() {
        WorkCalendar calendar = new WorkCalendar()
                .shift(LocalTime.of(22, 0), LocalTime.of(6, 0), MON_TO_SAT, 1.0);

        assertThat(calendar.windowsOn(MONDAY)).containsExactly(
                new TimeWindow(MONDAY.atTime(22, 0), MONDAY.plusDays(1).atTime(6, 0), 1.0));
    }

    @Test
    void aCalendarWithNoShiftsHasNoCapacity() {
        assertThat(new WorkCalendar().hasCapacity()).isFalse();
        assertThat(dayShift().hasCapacity()).isTrue();
    }
}
