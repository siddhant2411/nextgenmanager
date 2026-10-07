package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTimelineTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final int HORIZON = 365;

    /** 09:00–17:00, Monday to Saturday. */
    private static WorkCalendar dayShift(double rate) {
        return new WorkCalendar()
                .shift(LocalTime.of(9, 0), LocalTime.of(17, 0),
                        EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), rate)
                .weeklyOff(DayOfWeek.SUNDAY);
    }

    private static Slot place(ResourceTimeline timeline, WorkCalendar calendar, LocalDateTime earliest, long minutes) {
        Slot slot = timeline.findSlot(calendar, earliest, minutes, HORIZON).orElseThrow();
        timeline.block(slot.start(), slot.end());
        return slot;
    }

    @Test
    void aJobThatFillsTheShiftTakesTheWholeDay() {
        Slot slot = place(new ResourceTimeline(), dayShift(1.0), MONDAY.atTime(9, 0), 480);

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(9, 0), MONDAY.atTime(17, 0)));
    }

    @Test
    void aSecondJobCannotUseCapacityTheFirstAlreadyTook() {
        ResourceTimeline timeline = new ResourceTimeline();
        WorkCalendar calendar = dayShift(1.0);

        place(timeline, calendar, MONDAY.atTime(9, 0), 480);
        Slot second = place(timeline, calendar, MONDAY.atTime(9, 0), 480);

        assertThat(second).isEqualTo(new Slot(TUESDAY.atTime(9, 0), TUESDAY.atTime(17, 0)));
    }

    @Test
    void aLongJobPausesOvernightAndResumes() {
        Slot slot = place(new ResourceTimeline(), dayShift(1.0), MONDAY.atTime(9, 0), 720);

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(9, 0), TUESDAY.atTime(13, 0)));
    }

    @Test
    void aJobTooBigForTheGapGoesAfterTheBooking() {
        ResourceTimeline timeline = new ResourceTimeline();
        timeline.block(MONDAY.atTime(11, 0), MONDAY.atTime(12, 0));

        Slot slot = place(timeline, dayShift(1.0), MONDAY.atTime(9, 0), 180);

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(12, 0), MONDAY.atTime(15, 0)));
    }

    @Test
    void aJobThatFitsTheGapUsesIt() {
        ResourceTimeline timeline = new ResourceTimeline();
        timeline.block(MONDAY.atTime(11, 0), MONDAY.atTime(12, 0));

        Slot slot = place(timeline, dayShift(1.0), MONDAY.atTime(9, 0), 120);

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(9, 0), MONDAY.atTime(11, 0)));
    }

    @Test
    void aJobNeverSpansABookingThatSitsBetweenTwoShifts() {
        ResourceTimeline timeline = new ResourceTimeline();
        timeline.block(MONDAY.atTime(20, 0), MONDAY.atTime(22, 0));

        // 12 hours from Monday would pause overnight across the 20:00 booking, so it waits.
        Slot slot = place(timeline, dayShift(1.0), MONDAY.atTime(9, 0), 720);

        assertThat(slot).isEqualTo(new Slot(TUESDAY.atTime(9, 0), TUESDAY.plusDays(1).atTime(13, 0)));
    }

    @Test
    void breaksStretchTheJobAcrossMoreOfTheClock() {
        Slot slot = place(new ResourceTimeline(), dayShift(0.5), MONDAY.atTime(9, 0), 60);

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(9, 0), MONDAY.atTime(11, 0)));
    }

    @Test
    void aStartOutsideWorkingHoursWaitsForTheNextShift() {
        Slot evening = place(new ResourceTimeline(), dayShift(1.0), MONDAY.atTime(19, 30), 60);
        Slot midShift = place(new ResourceTimeline(), dayShift(1.0), MONDAY.atTime(14, 15), 60);

        assertThat(evening).isEqualTo(new Slot(TUESDAY.atTime(9, 0), TUESDAY.atTime(10, 0)));
        assertThat(midShift).isEqualTo(new Slot(MONDAY.atTime(14, 15), MONDAY.atTime(15, 15)));
    }

    @Test
    void aNightShiftAlreadyRunningCanBeJoinedPartWay() {
        WorkCalendar nights = new WorkCalendar()
                .shift(LocalTime.of(22, 0), LocalTime.of(6, 0), EnumSet.allOf(DayOfWeek.class), 1.0);

        Slot slot = place(new ResourceTimeline(), nights, TUESDAY.atTime(2, 0), 120);

        assertThat(slot).isEqualTo(new Slot(TUESDAY.atTime(2, 0), TUESDAY.atTime(4, 0)));
    }

    @Test
    void aZeroLengthJobLandsOnTheFirstFreeWorkingMinute() {
        ResourceTimeline timeline = new ResourceTimeline();
        timeline.block(MONDAY.atTime(9, 0), MONDAY.atTime(10, 0));

        Slot slot = timeline.findSlot(dayShift(1.0), MONDAY.atTime(8, 0), 0, HORIZON).orElseThrow();

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(10, 0), MONDAY.atTime(10, 0)));
    }

    @Test
    void overlappingAndTouchingBlocksMergeIntoOne() {
        ResourceTimeline timeline = new ResourceTimeline();
        timeline.block(MONDAY.atTime(9, 0), MONDAY.atTime(11, 0));
        timeline.block(MONDAY.atTime(13, 0), MONDAY.atTime(15, 0));
        timeline.block(MONDAY.atTime(10, 0), MONDAY.atTime(13, 0));

        Slot slot = place(timeline, dayShift(1.0), MONDAY.atTime(9, 0), 60);

        assertThat(slot).isEqualTo(new Slot(MONDAY.atTime(15, 0), MONDAY.atTime(16, 0)));
    }

    @Test
    void noSlotWhenTheCalendarNeverOpens() {
        assertThat(new ResourceTimeline().findSlot(new WorkCalendar(), MONDAY.atTime(9, 0), 60, HORIZON)).isEmpty();
    }
}
