package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Everything already committed on one resource — a machine, or a work centre that has none —
 * and the search for the next place a job fits.
 *
 * <p>A job may pause while the work centre is closed and resume when it reopens, but it never
 * shares the resource: once started it holds the machine until it ends, so it cannot span
 * another booking or a downtime.
 */
public class ResourceTimeline {

    private static final double EPSILON = 1e-6;

    /** Blocked intervals, start to end, kept merged so no two overlap or touch. */
    private final TreeMap<LocalDateTime, LocalDateTime> blocked = new TreeMap<>();

    public ResourceTimeline copy() {
        ResourceTimeline copy = new ResourceTimeline();
        copy.blocked.putAll(blocked);
        return copy;
    }

    public void block(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) return;

        Map.Entry<LocalDateTime, LocalDateTime> before = blocked.floorEntry(start);
        if (before != null && !before.getValue().isBefore(start)) {
            start = before.getKey();
            if (before.getValue().isAfter(end)) end = before.getValue();
        }
        Iterator<Map.Entry<LocalDateTime, LocalDateTime>> following = blocked.tailMap(start, true).entrySet().iterator();
        while (following.hasNext()) {
            Map.Entry<LocalDateTime, LocalDateTime> next = following.next();
            if (next.getKey().isAfter(end)) break;
            if (next.getValue().isAfter(end)) end = next.getValue();
            following.remove();
        }
        blocked.put(start, end);
    }

    /**
     * The earliest slot at or after {@code earliest} that gives the job {@code minutes} of
     * productive time, or empty if none opens within the horizon.
     */
    public Optional<Slot> findSlot(WorkCalendar calendar, LocalDateTime earliest, long minutes, int horizonDays) {
        LocalDateTime cursor = earliest;
        LocalDate lastDay = earliest.toLocalDate().plusDays(horizonDays);

        LocalDateTime start = null;
        LocalDateTime workedUntil = null;
        double remaining = minutes;

        // Begin a day early: last night's shift may still be running at `earliest`.
        for (LocalDate day = earliest.toLocalDate().minusDays(1); !day.isAfter(lastDay); day = day.plusDays(1)) {
            for (TimeWindow window : calendar.windowsOn(day)) {
                LocalDateTime t = window.start().isAfter(cursor) ? window.start() : cursor;

                while (t.isBefore(window.end())) {
                    LocalDateTime blockedUntil = blockedUntil(t);
                    boolean interrupted = start != null && !blocked.subMap(workedUntil, true, t, false).isEmpty();
                    if (blockedUntil != null || interrupted) {
                        // Something else holds the resource part-way through: start over after it.
                        start = null;
                        remaining = minutes;
                        if (blockedUntil != null) {
                            t = blockedUntil;
                            continue;
                        }
                    }
                    if (start == null) start = t;

                    Map.Entry<LocalDateTime, LocalDateTime> next = blocked.higherEntry(t);
                    LocalDateTime segmentEnd = next != null && next.getKey().isBefore(window.end())
                            ? next.getKey() : window.end();

                    double capacity = ChronoUnit.MINUTES.between(t, segmentEnd) * window.rate();
                    if (capacity + EPSILON >= remaining) {
                        long wallMinutes = (long) Math.ceil(remaining / window.rate() - EPSILON);
                        return Optional.of(new Slot(start, t.plusMinutes(wallMinutes)));
                    }
                    remaining -= capacity;
                    t = segmentEnd;
                    workedUntil = t;
                }
                if (t.isAfter(cursor)) cursor = t;
            }
        }
        return Optional.empty();
    }

    /**
     * The latest slot that ends at or before {@code latest} and starts no earlier than
     * {@code floor}, giving the job {@code minutes} of productive time — the backward-scheduling
     * mirror of {@link #findSlot}. Empty when the job cannot fit between the two.
     */
    public Optional<Slot> findLatestSlot(WorkCalendar calendar, LocalDateTime latest, long minutes, LocalDateTime floor) {
        LocalDateTime cursor = latest;
        LocalDate firstDay = floor.toLocalDate().minusDays(1);

        LocalDateTime end = null;
        LocalDateTime workedFrom = null;
        double remaining = minutes;

        for (LocalDate day = latest.toLocalDate(); !day.isBefore(firstDay); day = day.minusDays(1)) {
            List<TimeWindow> windows = new ArrayList<>(calendar.windowsOn(day));
            Collections.reverse(windows);
            for (TimeWindow window : windows) {
                LocalDateTime t = window.end().isBefore(cursor) ? window.end() : cursor;

                while (t.isAfter(window.start())) {
                    // Anything still to find from here would start before the floor.
                    if (!t.isAfter(floor)) return Optional.empty();
                    Map.Entry<LocalDateTime, LocalDateTime> prior = blocked.lowerEntry(t);
                    boolean busyJustBefore = prior != null && !prior.getValue().isBefore(t);
                    // While paused between windows the job still holds the resource.
                    boolean interrupted = end != null && !busyJustBefore && heldDuring(t, workedFrom);
                    if (busyJustBefore || interrupted) {
                        end = null;
                        remaining = minutes;
                        if (busyJustBefore) {
                            t = prior.getKey();
                            continue;
                        }
                    }
                    if (end == null) end = t;

                    LocalDateTime segmentStart = prior != null && prior.getValue().isAfter(window.start())
                            ? prior.getValue() : window.start();

                    double capacity = ChronoUnit.MINUTES.between(segmentStart, t) * window.rate();
                    if (capacity + EPSILON >= remaining) {
                        long wallMinutes = (long) Math.ceil(remaining / window.rate() - EPSILON);
                        LocalDateTime start = t.minusMinutes(wallMinutes);
                        return start.isBefore(floor) ? Optional.empty() : Optional.of(new Slot(start, end));
                    }
                    remaining -= capacity;
                    t = segmentStart;
                    workedFrom = t;
                }
                if (t.isBefore(cursor)) cursor = t;
            }
        }
        return Optional.empty();
    }

    /** True when anything is booked inside [from, until). */
    public boolean isBusyDuring(LocalDateTime from, LocalDateTime until) {
        return until.isAfter(from) && heldDuring(from, until);
    }

    private boolean heldDuring(LocalDateTime from, LocalDateTime until) {
        Map.Entry<LocalDateTime, LocalDateTime> entry = blocked.lowerEntry(until);
        return entry != null && entry.getValue().isAfter(from);
    }

    private LocalDateTime blockedUntil(LocalDateTime t) {
        Map.Entry<LocalDateTime, LocalDateTime> entry = blocked.floorEntry(t);
        return entry != null && entry.getValue().isAfter(t) ? entry.getValue() : null;
    }
}
