package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.CalendarOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CalendarOverrideRepository extends JpaRepository<CalendarOverride, Long> {
    Optional<CalendarOverride> findByHolidayCalendarIdAndOverrideDate(Long holidayCalendarId, LocalDate overrideDate);
}
