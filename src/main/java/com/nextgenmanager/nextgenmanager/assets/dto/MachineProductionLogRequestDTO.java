package com.nextgenmanager.nextgenmanager.assets.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MachineProductionLogRequestDTO {

    @NotNull
    private Long machineId;

    @NotNull
    private LocalDate productionDate;

    private Long shiftId;
    @PositiveOrZero
    private Integer plannedQuantity;
    @PositiveOrZero
    private Integer actualQuantity;
    @PositiveOrZero
    private Integer rejectedQuantity;
    @PositiveOrZero
    private Integer runtimeMinutes;
    @PositiveOrZero
    private Integer downtimeMinutes;
}
