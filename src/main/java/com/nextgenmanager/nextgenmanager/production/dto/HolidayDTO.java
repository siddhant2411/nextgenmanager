package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayDTO {
    private Long id;
    private LocalDate holidayDate;
    private String name;
    private boolean fullDay;
    private LocalTime startTime;
    private LocalTime endTime;
}
