package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.CalendarOverride;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarOverrideDTO {
    private Long id;
    private LocalDate overrideDate;
    private CalendarOverride.OverrideType overrideType;
    private String reason;
}
