package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.CalendarOverrideDTO;
import com.nextgenmanager.nextgenmanager.production.dto.HolidayCalendarDTO;
import com.nextgenmanager.nextgenmanager.production.dto.HolidayDTO;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.HolidayCalendar;

import java.time.LocalDate;
import java.util.List;

public interface HolidayCalendarService {

    HolidayCalendarDTO getById(Long id);

    HolidayCalendar getEntityById(Long id);

    List<HolidayCalendarDTO> getAll();

    HolidayCalendarDTO create(HolidayCalendarDTO dto);

    HolidayCalendarDTO update(Long id, HolidayCalendarDTO dto);

    void delete(Long id);

    // Holiday management
    HolidayDTO addHoliday(Long calendarId, HolidayDTO dto);

    void removeHoliday(Long calendarId, Long holidayId);

    List<HolidayDTO> getHolidays(Long calendarId, LocalDate from, LocalDate to);

    // Override management
    CalendarOverrideDTO addOverride(Long calendarId, CalendarOverrideDTO dto);

    void removeOverride(Long calendarId, Long overrideId);

    // Scheduling helper: is this date a working day?
    boolean isWorkingDay(Long calendarId, LocalDate date);
}
