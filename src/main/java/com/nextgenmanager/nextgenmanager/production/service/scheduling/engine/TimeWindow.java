package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A stretch of working time on a resource.
 *
 * @param rate productive minutes per wall-clock minute, in (0, 1]. A shift records how long its
 *             breaks are but not when they fall, so a break is spread across the shift rather
 *             than placed at an invented time: an 8-hour shift with a 30-minute break runs at
 *             450/480.
 */
public record TimeWindow(LocalDateTime start, LocalDateTime end, double rate) {

    public long wallMinutes() {
        return ChronoUnit.MINUTES.between(start, end);
    }

    public double productiveMinutes() {
        return wallMinutes() * rate;
    }
}
