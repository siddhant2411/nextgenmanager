package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class RoutingOperationDto {

    private Long id;
    private Integer sequenceNumber;
    private String name;

    private ProductionJobResponseDTO productionJob;
    private WorkCenterResponseDTO workCenter;

    private LaborRoleResponseDTO laborRole;
    private Integer numberOfOperators;
    private MachineDetailsResponseDTO machineDetails;
    private CostType costType;
    private BigDecimal fixedCostPerUnit;

    private BigDecimal setupTime;
    private BigDecimal runTime;

    private Boolean inspection;
    private String notes;

    // ---- Parallel Operation Fields ----
    private Boolean allowParallel;
    private String parallelPath;

    /** Explicit dependencies for this operation. Empty = no declared deps (legacy order). */
    private List<RoutingOperationDependencyDTO> dependencies;

    /** File attachments for this operation (drawings, SOPs, etc.) */
    private List<FileAttachment> attachments;
}
