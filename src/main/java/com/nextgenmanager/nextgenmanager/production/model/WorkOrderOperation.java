package com.nextgenmanager.nextgenmanager.production.model;


import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "WorkOrderOperation")
public class WorkOrderOperation {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ---- Parent Work Order ----
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routingOperationId")
    private RoutingOperation routingOperation;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private String operationName;           // Cutting, Welding

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId")
    private WorkCenter workCenter;

    /**
     * Specific machine assigned for this operation (machine-level scheduling).
     * Copied from RoutingOperation.machineDetails during WO explosion.
     * If null, scheduler will auto-assign least-loaded machine in the work center.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignedMachineId")
    private MachineDetails assignedMachine;


    // ---- Quantities ----
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal plannedQuantity;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal completedQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    @Column(length = 50)
    private String rejectionReasonCode;

    @Column(length = 50)
    private String scrapReasonCode;

    /**
     * How much qty this operation is allowed to process.
     * First operation: availableInputQuantity = plannedQuantity
     * Subsequent ops: starts at 0, incremented as previous op completes partial qty.
     * Dual-gate: completedQuantity cannot exceed availableInputQuantity.
     */
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal availableInputQuantity = BigDecimal.ZERO;

    private Date plannedStartDate;
    private Date plannedEndDate;

    private Date actualStartDate;
    private Date actualEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    private Boolean isMilestone = false;
    private Boolean allowOverCompletion = false;

    // ---- Parallel Operation Fields ----

    /**
     * IDs of WorkOrderOperations that must be COMPLETED before this operation can start.
     * Populated from RoutingOperationDependency when the work order is released.
     * Empty = no dependencies, this operation can start immediately.
     */
    @ElementCollection
    @CollectionTable(
            name = "WorkOrderOperationDependency",
            joinColumns = @JoinColumn(name = "workOrderOperationId")
    )
    @Column(name = "dependsOnOperationId")
    private Set<Long> dependsOnOperationIds = new HashSet<>();

    /**
     * Optional label matching RoutingOperation.parallelPath.
     * Used to group operations that belong to the same concurrent execution stream.
     */
    @Column(length = 50)
    private String parallelPath;

    /**
     * Timestamp when all dependencies for this operation were resolved (all became COMPLETED).
     * Set automatically when the last blocking dependency completes.
     */
    private Date dependencyResolvedDate;

    @OneToMany(mappedBy = "workOrderOperation", fetch = FetchType.LAZY)
    private List<WorkOrderLabourEntry> labourEntries = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;



}
