package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.CalendarOverrideDTO;
import com.nextgenmanager.nextgenmanager.production.dto.HolidayCalendarDTO;
import com.nextgenmanager.nextgenmanager.production.dto.HolidayDTO;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.CalendarOverride;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.Holiday;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.HolidayCalendar;
import com.nextgenmanager.nextgenmanager.production.repository.CalendarOverrideRepository;
import com.nextgenmanager.nextgenmanager.production.repository.HolidayCalendarRepository;
import com.nextgenmanager.nextgenmanager.production.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class HolidayCalendarServiceImpl implements HolidayCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(HolidayCalendarServiceImpl.class);

    @Autowired
    private HolidayCalendarRepository calendarRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private CalendarOverrideRepository overrideRepository;

    // ── Calendar CRUD ──

    @Override
    public HolidayCalendarDTO getById(Long id) {
        return toDTO(getEntityById(id));
    }

    @Override
    public HolidayCalendar getEntityById(Long id) {
        return calendarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday calendar not found: " + id));
    }

    @Override
    public List<HolidayCalendarDTO> getAll() {
        return calendarRepository.findAll().stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());
    }

    @Override
    public HolidayCalendarDTO create(HolidayCalendarDTO dto) {
        calendarRepository.findByName(dto.getName()).ifPresent(existing -> {
            throw new IllegalStateException("Calendar with name '" + dto.getName() + "' already exists.");
        });

        HolidayCalendar calendar = new HolidayCalendar();
        calendar.setName(dto.getName());
        calendar.setDescription(dto.getDescription());
        calendar.setActive(dto.isActive());
        calendar.setWeeklyOffDays(dto.getWeeklyOffDays());

        return toDTO(calendarRepository.save(calendar));
    }

    @Override
    public HolidayCalendarDTO update(Long id, HolidayCalendarDTO dto) {
        HolidayCalendar existing = getEntityById(id);

        calendarRepository.findByName(dto.getName()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new IllegalStateException("Calendar with name '" + dto.getName() + "' already exists.");
            }
        });

        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setActive(dto.isActive());
        existing.setWeeklyOffDays(dto.getWeeklyOffDays());

        return toDTO(calendarRepository.save(existing));
    }

    @Override
    public void delete(Long id) {
        HolidayCalendar calendar = getEntityById(id);
        calendarRepository.delete(calendar);
    }

    // ── Holiday Management ──

    @Override
    public HolidayDTO addHoliday(Long calendarId, HolidayDTO dto) {
        HolidayCalendar calendar = getEntityById(calendarId);

        Holiday holiday = new Holiday();
        holiday.setHolidayCalendar(calendar);
        holiday.setHolidayDate(dto.getHolidayDate());
        holiday.setName(dto.getName());
        holiday.setFullDay(dto.isFullDay());
        holiday.setStartTime(dto.getStartTime());
        holiday.setEndTime(dto.getEndTime());

        calendar.getHolidays().add(holiday);
        calendarRepository.save(calendar);

        return toHolidayDTO(holiday);
    }

    @Override
    public void removeHoliday(Long calendarId, Long holidayId) {
        HolidayCalendar calendar = getEntityById(calendarId);
        boolean removed = calendar.getHolidays().removeIf(h -> h.getId().equals(holidayId));
        if (!removed) {
            throw new ResourceNotFoundException("Holiday not found: " + holidayId);
        }
        calendarRepository.save(calendar);
    }

    @Override
    public List<HolidayDTO> getHolidays(Long calendarId, LocalDate from, LocalDate to) {
        getEntityById(calendarId); // validate exists
        return holidayRepository.findByHolidayCalendarIdAndHolidayDateBetween(calendarId, from, to)
                .stream()
                .map(this::toHolidayDTO)
                .collect(Collectors.toList());
    }

    // ── Override Management ──

    @Override
    public CalendarOverrideDTO addOverride(Long calendarId, CalendarOverrideDTO dto) {
        HolidayCalendar calendar = getEntityById(calendarId);

        CalendarOverride override = new CalendarOverride();
        override.setHolidayCalendar(calendar);
        override.setOverrideDate(dto.getOverrideDate());
        override.setOverrideType(dto.getOverrideType());
        override.setReason(dto.getReason());

        return toOverrideDTO(overrideRepository.save(override));
    }


   

    @Override
    public void removeOverride(Long calendarId, Long overrideId) {
        CalendarOverride override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("Override not found: " + overrideId));
        if (!override.getHolidayCalendar().getId().equals(calendarId)) {
            throw new ResourceNotFoundException("Override not found in calendar: " + calendarId);
        }
        overrideRepository.delete(override);
    }

    // ── Scheduling Helper ──

    @Override
    public boolean isWorkingDay(Long calendarId, LocalDate date) {
        HolidayCalendar calendar = getEntityById(calendarId);

        // 1. Check override first (highest priority)
        var override = overrideRepository.findByHolidayCalendarIdAndOverrideDate(calendarId, date);
        if (override.isPresent()) {
            return override.get().getOverrideType() == CalendarOverride.OverrideType.WORKING;
        }

        // 2. Check full-day holiday
        var holiday = holidayRepository.findByHolidayCalendarIdAndHolidayDate(calendarId, date);
        if (holiday.isPresent() && holiday.get().isFullDay()) {
            return false;
        }

        // 3. Check weekly off days
        if (calendar.getWeeklyOffDays() != null && calendar.getWeeklyOffDays().contains(date.getDayOfWeek())) {
            return false;
        }

        return true;
    }

    // ── Mappers ──

    private HolidayCalendarDTO toDTO(HolidayCalendar cal) {
        List<HolidayDTO> holidays = cal.getHolidays() != null
                ? cal.getHolidays().stream().map(this::toHolidayDTO).collect(Collectors.toList())
                : List.of();

        List<CalendarOverrideDTO> overrides = cal.getOverrides() != null
                ? cal.getOverrides().stream().map(this::toOverrideDTO).collect(Collectors.toList())
                : List.of();

        return HolidayCalendarDTO.builder()
                .id(cal.getId())
                .name(cal.getName())
                .description(cal.getDescription())
                .active(cal.isActive())
                .weeklyOffDays(cal.getWeeklyOffDays())
                .holidays(holidays)
                .overrides(overrides)
                .build();
    }

    private HolidayCalendarDTO toSummaryDTO(HolidayCalendar cal) {
        return HolidayCalendarDTO.builder()
                .id(cal.getId())
                .name(cal.getName())
                .description(cal.getDescription())
                .active(cal.isActive())
                .weeklyOffDays(cal.getWeeklyOffDays())
                .build();
    }

    private HolidayDTO toHolidayDTO(Holiday h) {
        return HolidayDTO.builder()
                .id(h.getId())
                .holidayDate(h.getHolidayDate())
                .name(h.getName())
                .fullDay(h.isFullDay())
                .startTime(h.getStartTime())
                .endTime(h.getEndTime())
                .build();
    }

    private CalendarOverrideDTO toOverrideDTO(CalendarOverride o) {
        return CalendarOverrideDTO.builder()
                .id(o.getId())
                .overrideDate(o.getOverrideDate())
                .overrideType(o.getOverrideType())
                .reason(o.getReason())
                .build();
    }
}
