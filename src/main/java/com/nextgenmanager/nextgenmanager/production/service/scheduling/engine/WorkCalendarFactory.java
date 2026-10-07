package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.CalendarOverride;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.Holiday;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.HolidayCalendar;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenterShift;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.EnumSet;

/** Reads a work centre's shifts and holiday calendar into a {@link WorkCalendar}. */
public final class WorkCalendarFactory {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private WorkCalendarFactory() {
    }

    public static WorkCalendar from(WorkCenter workCenter) {
        WorkCalendar calendar = new WorkCalendar();
        double loadCap = loadCap(workCenter.getMaxLoadPercentage());

        if (workCenter.getShifts() == null || workCenter.getShifts().isEmpty()) {
            addHoursPerDayShift(calendar, workCenter.getAvailableHoursPerDay(), loadCap);
        } else {
            for (WorkCenterShift shift : workCenter.getShifts()) {
                if (!shift.isActive() || shift.getDeletedDate() != null) continue;
                if (shift.getActiveDays() == null || shift.getActiveDays().isEmpty()) continue;

                int wallMinutes = wallMinutes(shift.getStartTime(), shift.getEndTime());
                int productive = shift.getPlannedCapacityMinutes() != null && shift.getPlannedCapacityMinutes() > 0
                        ? shift.getPlannedCapacityMinutes()
                        : wallMinutes - (shift.getBreakMinutes() != null ? shift.getBreakMinutes() : 0);
                double rate = Math.min(1.0, (double) productive / wallMinutes) * loadCap;
                calendar.shift(shift.getStartTime(), shift.getEndTime(), shift.getActiveDays(), rate);
            }
        }

        HolidayCalendar holidays = workCenter.getHolidayCalendar();
        if (holidays == null) return calendar;

        if (holidays.getWeeklyOffDays() != null) {
            holidays.getWeeklyOffDays().forEach(calendar::weeklyOff);
        }
        if (holidays.getHolidays() != null) {
            for (Holiday holiday : holidays.getHolidays()) {
                if (holiday.isFullDay() || holiday.getStartTime() == null || holiday.getEndTime() == null) {
                    calendar.holiday(holiday.getHolidayDate());
                } else {
                    calendar.partialHoliday(holiday.getHolidayDate(), holiday.getStartTime(), holiday.getEndTime());
                }
            }
        }
        if (holidays.getOverrides() != null) {
            for (CalendarOverride override : holidays.getOverrides()) {
                if (override.getOverrideType() == CalendarOverride.OverrideType.OFF) {
                    calendar.forceOff(override.getOverrideDate());
                } else {
                    calendar.forceWorking(override.getOverrideDate());
                }
            }
        }
        return calendar;
    }

    /** A work centre with no shifts defined but an hours-per-day figure: one shift from 09:00. */
    private static void addHoursPerDayShift(WorkCalendar calendar, BigDecimal hoursPerDay, double rate) {
        if (hoursPerDay == null || hoursPerDay.signum() <= 0) return;
        int minutes = Math.min(hoursPerDay.multiply(BigDecimal.valueOf(60)).intValue(), MINUTES_PER_DAY);
        // More than fifteen hours from 09:00 would run past midnight, so start at midnight instead.
        LocalTime start = minutes <= 15 * 60 ? LocalTime.of(9, 0) : LocalTime.MIDNIGHT;
        calendar.shift(start, start.plusMinutes(minutes), EnumSet.allOf(DayOfWeek.class), rate);
    }

    private static double loadCap(Integer maxLoadPercentage) {
        if (maxLoadPercentage == null || maxLoadPercentage <= 0 || maxLoadPercentage >= 100) return 1.0;
        return maxLoadPercentage / 100.0;
    }

    private static int wallMinutes(LocalTime start, LocalTime end) {
        int minutes = (int) Duration.between(start, end).toMinutes();
        return minutes > 0 ? minutes : minutes + MINUTES_PER_DAY;
    }
}
