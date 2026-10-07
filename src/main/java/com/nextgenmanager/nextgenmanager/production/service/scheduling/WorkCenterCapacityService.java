package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.WorkCalendarFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Calculates available working minutes for a WorkCenter on a given date.
 *
 * <p>The rules — overrides, weekly off days, holidays, shifts and breaks — live in
 * {@link com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.WorkCalendar},
 * the same calendar the scheduler places operations against, so a day's capacity here always
 * agrees with what the scheduler will actually book.
 */
@Service
public class WorkCenterCapacityService {

    /**
     * Returns the available working minutes for a work center on a specific date.
     */
    public int getAvailableMinutes(WorkCenter workCenter, LocalDate date) {
        return WorkCalendarFactory.from(workCenter).productiveMinutesOn(date);
    }
}
