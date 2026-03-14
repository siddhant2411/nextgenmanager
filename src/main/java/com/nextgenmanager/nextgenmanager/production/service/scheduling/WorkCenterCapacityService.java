package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.CalendarOverride;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.Holiday;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.HolidayCalendar;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenterShift;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Calculates available working minutes for a WorkCenter on a given date.
 *
 * Logic:
 * 1. Check if it's a weekly off day → 0 minutes
 * 2. Check CalendarOverride (WORKING→override to working day, OFF→override to off)
 * 3. Check Holiday (full-day→0, partial→subtract partial hours from shift)
 * 4. Sum up all active shifts for the day, minus break minutes
 */
@Service
public class WorkCenterCapacityService {

    private static final Logger logger = LoggerFactory.getLogger(WorkCenterCapacityService.class);

    /**
     * Returns the available working minutes for a work center on a specific date.
     */
    public int getAvailableMinutes(WorkCenter workCenter, LocalDate date) {

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        HolidayCalendar calendar = workCenter.getHolidayCalendar();

        // ── Step 1: Check CalendarOverride first (takes precedence) ──
        if (calendar != null && calendar.getOverrides() != null) {
            for (CalendarOverride override : calendar.getOverrides()) {
                if (override.getOverrideDate().equals(date)) {
                    if (override.getOverrideType() == CalendarOverride.OverrideType.OFF) {
                        logger.debug("WC {} on {} → OFF (calendar override: {})",
                                workCenter.getCenterCode(), date, override.getReason());
                        return 0;
                    }
                    // WORKING override: skip weekly-off and holiday checks, treat as working day
                    logger.debug("WC {} on {} → WORKING (calendar override: {})",
                            workCenter.getCenterCode(), date, override.getReason());
                    return calculateShiftMinutes(workCenter, dayOfWeek, date, calendar);
                }
            }
        }

        // ── Step 2: Check weekly off days ──
        if (calendar != null && calendar.getWeeklyOffDays() != null
                && calendar.getWeeklyOffDays().contains(dayOfWeek)) {
            logger.debug("WC {} on {} → 0 min (weekly off: {})",
                    workCenter.getCenterCode(), date, dayOfWeek);
            return 0;
        }

        // ── Step 3: Check holidays ──
        if (calendar != null && calendar.getHolidays() != null) {
            for (Holiday holiday : calendar.getHolidays()) {
                if (holiday.getHolidayDate().equals(date)) {
                    if (holiday.isFullDay()) {
                        logger.debug("WC {} on {} → 0 min (full-day holiday: {})",
                                workCenter.getCenterCode(), date, holiday.getName());
                        return 0;
                    }
                    // Partial holiday — subtract the holiday window from shift hours
                    return calculateShiftMinutesExcludingPartialHoliday(
                            workCenter, dayOfWeek, holiday);
                }
            }
        }

        // ── Step 4: Normal working day ──
        return calculateShiftMinutes(workCenter, dayOfWeek, date, calendar);
    }

    /**
     * Sums all active shift minutes for the given day of week, minus break minutes.
     */
    private int calculateShiftMinutes(WorkCenter workCenter, DayOfWeek dayOfWeek,
                                       LocalDate date, HolidayCalendar calendar) {
        if (workCenter.getShifts() == null || workCenter.getShifts().isEmpty()) {
            // Fallback to availableHoursPerDay if no shifts defined
            if (workCenter.getAvailableHoursPerDay() != null) {
                return workCenter.getAvailableHoursPerDay().multiply(java.math.BigDecimal.valueOf(60)).intValue();
            }
            return 0;
        }

        int totalMinutes = 0;

        for (WorkCenterShift shift : workCenter.getShifts()) {
            if (!shift.isActive()) continue;
            if (shift.getDeletedDate() != null) continue;

            Set<DayOfWeek> activeDays = shift.getActiveDays();
            if (activeDays == null || !activeDays.contains(dayOfWeek)) continue;

            // If plannedCapacityMinutes is set, use it directly
            if (shift.getPlannedCapacityMinutes() != null && shift.getPlannedCapacityMinutes() > 0) {
                totalMinutes += shift.getPlannedCapacityMinutes();
            } else {
                // Calculate from start/end time minus breaks
                int shiftMinutes = calculateDurationMinutes(shift.getStartTime(), shift.getEndTime());
                int breakMinutes = shift.getBreakMinutes() != null ? shift.getBreakMinutes() : 0;
                totalMinutes += Math.max(0, shiftMinutes - breakMinutes);
            }
        }

        logger.debug("WC {} on {} ({}) → {} available minutes",
                workCenter.getCenterCode(), date, dayOfWeek, totalMinutes);
        return totalMinutes;
    }

    /**
     * For partial holidays: calculate normal shift minutes minus the overlapping holiday window.
     * Handles both normal shifts (e.g. 09:00-17:00) and overnight shifts (e.g. 22:00-06:00).
     */
    private int calculateShiftMinutesExcludingPartialHoliday(
            WorkCenter workCenter, DayOfWeek dayOfWeek, Holiday holiday) {

        if (workCenter.getShifts() == null) return 0;

        int totalMinutes = 0;

        for (WorkCenterShift shift : workCenter.getShifts()) {
            if (!shift.isActive()) continue;
            if (shift.getDeletedDate() != null) continue;
            if (shift.getActiveDays() == null || !shift.getActiveDays().contains(dayOfWeek)) continue;

            int shiftMinutes;
            if (shift.getPlannedCapacityMinutes() != null && shift.getPlannedCapacityMinutes() > 0) {
                shiftMinutes = shift.getPlannedCapacityMinutes();
            } else {
                int raw = calculateDurationMinutes(shift.getStartTime(), shift.getEndTime());
                int breakMinutes = shift.getBreakMinutes() != null ? shift.getBreakMinutes() : 0;
                shiftMinutes = Math.max(0, raw - breakMinutes);
            }

            // Calculate overlap between shift and holiday window
            // For overnight shifts, simple min/max comparison doesn't work
            boolean isOvernightShift = !shift.getEndTime().isAfter(shift.getStartTime());
            LocalTime hStart = holiday.getStartTime();
            LocalTime hEnd = holiday.getEndTime();

            int overlapMinutes = 0;
            if (isOvernightShift) {
                // Overnight shift: treat as two segments [shiftStart, 23:59] and [00:00, shiftEnd]
                // Segment 1: shiftStart → midnight
                LocalTime seg1Start = maxTime(shift.getStartTime(), hStart);
                LocalTime seg1End = minTime(LocalTime.of(23, 59), hEnd);
                if (seg1Start.isBefore(seg1End) && !seg1Start.isBefore(shift.getStartTime()) == false) {
                    // Only count if overlap is within the shift's evening portion
                    if (!hEnd.isBefore(shift.getStartTime()) && !hStart.isAfter(LocalTime.of(23, 59))) {
                        LocalTime oStart = maxTime(shift.getStartTime(), hStart);
                        LocalTime oEnd = minTime(LocalTime.of(23, 59), hEnd);
                        if (oStart.isBefore(oEnd)) {
                            overlapMinutes += (int) Duration.between(oStart, oEnd).toMinutes();
                        }
                    }
                }
                // Segment 2: midnight → shiftEnd
                if (!hEnd.isBefore(LocalTime.MIN) && !hStart.isAfter(shift.getEndTime())) {
                    LocalTime oStart = maxTime(LocalTime.MIN, hStart);
                    LocalTime oEnd = minTime(shift.getEndTime(), hEnd);
                    if (oStart.isBefore(oEnd)) {
                        overlapMinutes += (int) Duration.between(oStart, oEnd).toMinutes();
                    }
                }
            } else {
                // Normal shift: simple overlap
                LocalTime overlapStart = maxTime(shift.getStartTime(), hStart);
                LocalTime overlapEnd = minTime(shift.getEndTime(), hEnd);
                if (overlapStart.isBefore(overlapEnd)) {
                    overlapMinutes = (int) Duration.between(overlapStart, overlapEnd).toMinutes();
                }
            }

            shiftMinutes = Math.max(0, shiftMinutes - overlapMinutes);
            totalMinutes += shiftMinutes;
        }

        logger.debug("WC {} on partial holiday {} → {} available minutes",
                workCenter.getCenterCode(), holiday.getName(), totalMinutes);
        return totalMinutes;
    }

    private int calculateDurationMinutes(LocalTime start, LocalTime end) {
        if (end.isAfter(start)) {
            return (int) Duration.between(start, end).toMinutes();
        }
        // Overnight shift: e.g., 22:00 to 06:00
        return (int) (Duration.between(start, LocalTime.MAX).toMinutes()
                + Duration.between(LocalTime.MIN, end).toMinutes() + 1);
    }

    private LocalTime maxTime(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalTime minTime(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
