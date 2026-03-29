package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderEventType;
import lombok.*;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WorkOrderHistoryDTO {

    private Long id;
    private WorkOrderEventType eventType;
    // ---- What changed ----
    private String fieldName;

    private String oldValue;
    private String newValue;

    // ---- Who & when ----
    private String performedBy;   // username / system
    private Date performedAt;
}
