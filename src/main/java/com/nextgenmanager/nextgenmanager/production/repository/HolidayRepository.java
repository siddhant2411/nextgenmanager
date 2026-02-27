package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    Optional<Holiday> findByHolidayCalendarIdAndHolidayDate(Long holidayCalendarId, LocalDate holidayDate);
    List<Holiday> findByHolidayCalendarIdAndHolidayDateBetween(Long holidayCalendarId, LocalDate from, LocalDate to);
}
