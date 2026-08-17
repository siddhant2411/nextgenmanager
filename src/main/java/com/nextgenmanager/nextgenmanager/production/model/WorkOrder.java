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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;

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

    /**
     * The finished items this work order produces, one line each.
     * Ordered by {@code lineNumber} so callers get a stable, user-visible sequence.
     */
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<WorkOrderLine> lines = new ArrayList<>();

    /** Set when this work order was created by splitting another. Distinct from {@link #parentWorkOrder}. */
    private Integer splitFromWorkOrderId;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderMaterial> materials;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderOperation> operations;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderTestResult> testResults;

    @Enumerated(EnumType.STRING)
    private WorkOrderSourceType sourceType;

    /**
     * Free-text reference for a MANUAL source — a job card, an email, a customer's PO number.
     *
     * <p>Only MANUAL uses it. A SALES_ORDER or PARENT_WORK_ORDER source is referenced by the
     * {@link #salesOrder} / {@link #parentWorkOrder} relations, which point at a real record;
     * a manual order has nothing to point at, so the reference is whatever the operator types.
     */
    private String referenceDocument;

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

    // ─── Single-line compatibility shim ───────────────────────────────────────
    // Transitional. Every work order currently has exactly one line (backfilled by V150), so
    // these accessors let the ~23 call sites that still read the work order's BOM / routing /
    // planned quantity keep working unchanged. They deliberately THROW on a multi-line work
    // order rather than silently answering for line 1 — a wrong-but-plausible answer here would
    // mean producing the wrong item or costing against the wrong BOM. Multi-line creation stays
    // blocked until every caller is line-aware, at which point this whole block is deleted.

    /** Live, non-deleted lines in line-number order. */
    public List<WorkOrderLine> activeLines() {
        if (lines == null) return List.of();
        return lines.stream().filter(l -> l.getDeletedDate() == null).toList();
    }

    public boolean isMultiLine() {
        return activeLines().size() > 1;
    }

    /**
     * The one and only line of a single-line work order.
     *
     * @throws IllegalStateException if this work order does not have exactly one active line —
     *         the caller must be rewritten to iterate {@link #activeLines()}.
     */
    public WorkOrderLine soleLine() {
        List<WorkOrderLine> active = activeLines();
        if (active.size() != 1) {
            throw new IllegalStateException(
                    "WorkOrder " + workOrderNumber + " has " + active.size()
                            + " active lines; this caller assumes exactly one and must be made "
                            + "line-aware (iterate activeLines()).");
        }
        return active.get(0);
    }

    // The header's bomId / routeId / plannedQuantity columns are kept as a MIRROR of line 1 while
    // every work order is single-line, and are dropped in V152. Lombok's generated getters are
    // left to read the fields directly on purpose: routing them through activeLines() would force
    // a lazy load of the lines collection on every call, which risks LazyInitializationException
    // in the DTO mappers and adds a query to ~23 existing call sites. The "exactly one line"
    // guarantee is instead enforced where it belongs — in the service layer, which refuses to
    // build a multi-line work order until every consumer has been made line-aware.

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
