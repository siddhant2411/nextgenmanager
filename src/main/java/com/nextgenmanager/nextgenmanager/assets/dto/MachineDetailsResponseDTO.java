package com.nextgenmanager.nextgenmanager.assets.dto;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterMachineDetailsDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MachineDetailsResponseDTO {
    private int id;
    private String machineCode;
    private String machineName;
    private String description;
    private BigDecimal costPerHour;
    private BigDecimal availableHoursPerDay;
    private MachineDetails.MachineStatus machineStatus;
    private WorkCenterMachineDetailsDTO workCenter;

}
