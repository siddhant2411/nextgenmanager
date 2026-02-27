package com.nextgenmanager.nextgenmanager.assets.dto;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MachineStatusChangeRequestDTO {

    @NotNull
    private MachineDetails.MachineStatus newStatus;

    private String reason;

    private LocalDate startDate;

    private LocalDate endDate;
}
