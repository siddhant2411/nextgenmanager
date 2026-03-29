package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.CalendarOverrideDTO;
import com.nextgenmanager.nextgenmanager.production.dto.HolidayCalendarDTO;
import com.nextgenmanager.nextgenmanager.production.dto.HolidayDTO;
import com.nextgenmanager.nextgenmanager.production.service.HolidayCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/calendar")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class HolidayCalendarController {

    private static final Logger logger = LoggerFactory.getLogger(HolidayCalendarController.class);

    @Autowired
    private HolidayCalendarService calendarService;

    // ── Calendar CRUD ──

    @GetMapping
    public ResponseEntity<List<HolidayCalendarDTO>> getAll() {
        return ResponseEntity.ok(calendarService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(calendarService.getById(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody HolidayCalendarDTO dto) {
        try {
            HolidayCalendarDTO created = calendarService.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody HolidayCalendarDTO dto) {
        try {
            return ResponseEntity.ok(calendarService.update(id, dto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            calendarService.delete(id);
            return ResponseEntity.ok("Calendar deleted successfully");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Holiday Management ──

    @PostMapping("/{calendarId}/holidays")
    public ResponseEntity<?> addHoliday(@PathVariable Long calendarId, @RequestBody HolidayDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.addHoliday(calendarId, dto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{calendarId}/holidays/{holidayId}")
    public ResponseEntity<?> removeHoliday(@PathVariable Long calendarId, @PathVariable Long holidayId) {
        try {
            calendarService.removeHoliday(calendarId, holidayId);
            return ResponseEntity.ok("Holiday removed");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{calendarId}/holidays")
    public ResponseEntity<?> getHolidays(
            @PathVariable Long calendarId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(calendarService.getHolidays(calendarId, from, to));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Override Management ──

    @PostMapping("/{calendarId}/overrides")
    public ResponseEntity<?> addOverride(@PathVariable Long calendarId, @RequestBody CalendarOverrideDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.addOverride(calendarId, dto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{calendarId}/overrides/{overrideId}")
    public ResponseEntity<?> removeOverride(@PathVariable Long calendarId, @PathVariable Long overrideId) {
        try {
            calendarService.removeOverride(calendarId, overrideId);
            return ResponseEntity.ok("Override removed");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Scheduling Query ──

    @GetMapping("/{calendarId}/is-working-day")
    public ResponseEntity<?> isWorkingDay(
            @PathVariable Long calendarId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            boolean working = calendarService.isWorkingDay(calendarId, date);
            return ResponseEntity.ok(Map.of("date", date.toString(), "workingDay", working));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
