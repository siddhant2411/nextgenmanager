package com.nextgenmanager.nextgenmanager.assets.dto;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterMachineDetailsDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MachineDetailsResponseDTO {
    private long id;
    private String machineCode;
    private String machineName;
    private String description;
    private BigDecimal costPerHour;
    private MachineDetails.MachineStatus machineStatus;
    private WorkCenterMachineDetailsDTO workCenter;
    private LocalDateTime lastUpdate;

}
