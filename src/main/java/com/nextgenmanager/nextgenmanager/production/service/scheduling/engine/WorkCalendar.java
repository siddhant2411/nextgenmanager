package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When a work centre is open, to the minute.
 *
 * <p>Plain data with no persistence types, so the scheduling engine can be exercised without a
 * database. {@link WorkCalendarFactory} builds one from a work centre.
 *
 * <p>Precedence for a given date, highest first: a calendar override, the weekly off day, a
 * holiday, then the shift pattern.
 */
public class WorkCalendar {

    /** A shift that ends at or before its start time runs past midnight into the next day. */
    public record Shift(LocalTime start, LocalTime end, Set<DayOfWeek> days, double rate) {
    }

    private record Closure(LocalTime start, LocalTime end) {
    }

    private final List<Shift> shifts = new ArrayList<>();
    private final Set<DayOfWeek> weeklyOffDays = EnumSet.noneOf(DayOfWeek.class);
    private final Set<LocalDate> fullHolidays = new HashSet<>();
    private final Map<LocalDate, Closure> partialHolidays = new HashMap<>();
    private final Set<LocalDate> forcedWorkingDays = new HashSet<>();
    private final Set<LocalDate> forcedOffDays = new HashSet<>();

    public WorkCalendar shift(LocalTime start, LocalTime end, Set<DayOfWeek> days, double rate) {
        if (rate > 0 && !days.isEmpty()) {
            shifts.add(new Shift(start, end, EnumSet.copyOf(days), Math.min(rate, 1.0)));
        }
        return this;
    }

    public WorkCalendar weeklyOff(DayOfWeek day) {
        weeklyOffDays.add(day);
        return this;
    }

    public WorkCalendar holiday(LocalDate date) {
        fullHolidays.add(date);
        return this;
    }

    /** The work centre is closed between the two times on that date and open for the rest. */
    public WorkCalendar partialHoliday(LocalDate date, LocalTime closedFrom, LocalTime closedUntil) {
        partialHolidays.put(date, new Closure(closedFrom, closedUntil));
        return this;
    }

    public WorkCalendar forceWorking(LocalDate date) {
        forcedWorkingDays.add(date);
        return this;
    }

    public WorkCalendar forceOff(LocalDate date) {
        forcedOffDays.add(date);
        return this;
    }

    /** False when no shift ever opens, so no amount of searching forward will find a slot. */
    public boolean hasCapacity() {
        return !shifts.isEmpty();
    }

    /**
     * Working windows that START on the given date, earliest first. A night shift's window
     * belongs to the date it starts on and extends into the next.
     */
    public List<TimeWindow> windowsOn(LocalDate date) {
        if (forcedOffDays.contains(date)) return List.of();

        boolean forcedWorking = forcedWorkingDays.contains(date);
        if (!forcedWorking && (weeklyOffDays.contains(date.getDayOfWeek()) || fullHolidays.contains(date))) {
            return List.of();
        }

        List<Shift> todays = shifts.stream().filter(s -> s.days().contains(date.getDayOfWeek())).toList();
        // A day declared working that no shift normally covers (a working Sunday) runs every
        // shift; otherwise the override would open the day and leave it with nothing to do.
        if (todays.isEmpty() && forcedWorking) todays = shifts;

        Closure closure = forcedWorking ? null : partialHolidays.get(date);

        List<TimeWindow> windows = new ArrayList<>();
        for (Shift s : todays) {
            LocalDateTime start = date.atTime(s.start());
            LocalDateTime end = s.end().isAfter(s.start()) ? date.atTime(s.end()) : date.plusDays(1).atTime(s.end());
            if (closure == null) {
                windows.add(new TimeWindow(start, end, s.rate()));
                continue;
            }
            LocalDateTime closedFrom = date.atTime(closure.start());
            LocalDateTime closedUntil = date.atTime(closure.end());
            if (start.isBefore(closedFrom)) {
                windows.add(new TimeWindow(start, earlier(end, closedFrom), s.rate()));
            }
            if (end.isAfter(closedUntil)) {
                windows.add(new TimeWindow(later(start, closedUntil), end, s.rate()));
            }
        }
        windows.sort(Comparator.comparing(TimeWindow::start));
        return windows;
    }

    public int productiveMinutesOn(LocalDate date) {
        return (int) Math.floor(windowsOn(date).stream().mapToDouble(TimeWindow::productiveMinutes).sum() + 1e-6);
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
