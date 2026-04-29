package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderOperationDTO {

    private Long id;

    private RoutingOperationDto routingOperation;

    private Integer sequence;

    private String operationName;

    private WorkCenterResponseDTO workCenter;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private BigDecimal rejectedQuantity;

    private String rejectionReasonCode;

    private String scrapReasonCode;

    private Date plannedStartDate;
    private Date plannedEndDate;

    private Date actualStartDate;
    private Date actualEndDate;

    private OperationStatus status;

    private Boolean isMilestone;

    private Boolean allowOverCompletion;

    // ---- Parallel Operation Fields ----

    /**
     * How much input quantity is available for this operation to process.
     * Set to plannedQuantity when op is released with no dependencies.
     * For sequential ops: incremented as the upstream op completes partial batches.
     */
    private BigDecimal availableInputQuantity;

    /**
     * IDs of WorkOrderOperations that must be COMPLETED before this can start.
     * Empty = no blocking dependencies (operation is independent or first in chain).
     */
    private Set<Long> dependsOnOperationIds;

    /**
     * Parallel path label. Operations sharing the same label belong to the same
     * concurrent execution stream (e.g. "PATH_A", "PATH_B").
     */
    private String parallelPath;

    /**
     * Timestamp when all dependencies for this operation were resolved.
     * Null until the last blocking dependency transitions to COMPLETED.
     */
    private Date dependencyResolvedDate;
}
