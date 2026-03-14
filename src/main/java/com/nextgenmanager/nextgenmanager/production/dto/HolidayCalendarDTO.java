package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayCalendarDTO {
    private Long id;
    private String name;
    private String description;
    private boolean active;
    private Set<DayOfWeek> weeklyOffDays;
    private List<HolidayDTO> holidays;
    private List<CalendarOverrideDTO> overrides;
}
