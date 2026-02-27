package com.nextgenmanager.nextgenmanager.assets.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class MachineProductionLogResponseDTO {
    private Long id;
    private Long machineId;
    private LocalDate productionDate;
    private Long shiftId;
    private Integer plannedQuantity;
    private Integer actualQuantity;
    private Integer rejectedQuantity;
    private Integer runtimeMinutes;
    private Integer downtimeMinutes;
}
