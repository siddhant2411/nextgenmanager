package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import com.nextgenmanager.nextgenmanager.production.model.Routing;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Where(clause = "deletedDate IS NULL")
@Table(name = "workOrder")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(cascade = CascadeType.DETACH, optional = true)
    @JoinColumn(name = "salesOrderId")
    private SalesOrder salesOrder;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "parentWorkOrderId")
    private WorkOrder parentWorkOrder;

    @Enumerated(EnumType.STRING)  // Use STRING instead of ORDINAL for safety
    private WorkOrderStatus workOrderStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private WorkOrderPriority priority = WorkOrderPriority.NORMAL;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    @Column(unique = true, nullable = false)
    private String workOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bomId", referencedColumnName = "id")
    private Bom bom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routeId", referencedColumnName = "id")
    private Routing routing;


    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderMaterial> materials;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderOperation> operations;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderTestResult> testResults;

    @Enumerated(EnumType.STRING)
    private WorkOrderSourceType sourceType;

    private String remarks;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean allowBackflush = false;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId")
    private WorkCenter workCenter;

    private Date dueDate;

    private Date plannedStartDate;

    private Date plannedEndDate;

    private Date actualStartDate;

    private Date actualEndDate;

    // ---- Scheduling & Estimation Fields ----

    /** Estimated total production time in minutes (sum of all ops: setup + run × qty) */
    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedProductionMinutes;

    /** Estimated total cost (material + labor + machine + overhead) */
    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedTotalCost;

    /** Whether this WO was auto-scheduled or manually dated */
    private Boolean autoScheduled = false;

    /** Who/what last scheduled it */
    @Column(length = 100)
    private String scheduledBy;

    private Date scheduledAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    // ─── Yield Metrics (computed, not persisted) ──────────────────────────────

    public BigDecimal getTotalOperationGoodQuantity() {
        if (operations == null) return BigDecimal.ZERO;
        return operations.stream()
                .map(WorkOrderOperation::getCompletedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalOperationRejectedQuantity() {
        if (operations == null) return BigDecimal.ZERO;
        return operations.stream()
                .map(WorkOrderOperation::getRejectedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalOperationScrapQuantity() {
        if (operations == null) return BigDecimal.ZERO;
        return operations.stream()
                .map(WorkOrderOperation::getScrappedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getFirstPassYield() {
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return getTotalOperationGoodQuantity()
                .divide(plannedQuantity, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getReworkRate() {
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return getTotalOperationRejectedQuantity()
                .divide(plannedQuantity, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getScrapRate() {
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return getTotalOperationScrapQuantity()
                .divide(plannedQuantity, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getOverallYield() {
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal goodAndRework = getTotalOperationGoodQuantity().add(getTotalOperationRejectedQuantity());
        return goodAndRework
                .divide(plannedQuantity, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
