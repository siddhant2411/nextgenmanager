package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
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

/**
 * One finished item being manufactured within a {@link WorkOrder}.
 *
 * <p>The line — not the work order — is the unit of manufacture. Everything about *making one
 * item* lives here (item, BOM, routing, quantities, materials, operations, tests, yield, cost);
 * the work order header carries only the shared administrative envelope (number, customer/SO,
 * priority, dates, release).
 *
 * <p>Note that {@link #inventoryItem} is stored explicitly rather than re-derived from
 * {@code bom.getParentInventoryItem()}. A line's identity must survive BOM revision, and the
 * historical coupling of "produced item == the BOM's parent item" was exactly what made the
 * single-item assumption invisible throughout the codebase.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Where(clause = "deletedDate IS NULL")
@Table(name = "workOrderLine",
        indexes = {
                @Index(name = "idx_wol_workorder", columnList = "workOrderId"),
                @Index(name = "idx_wol_item", columnList = "inventoryItemId")
        })
public class WorkOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    /** Position of this line within the work order, 1-based. */
    @Column(nullable = false)
    private Integer lineNumber;

    /** The finished item this line produces. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemId")
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bomId")
    private Bom bom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routeId")
    private Routing routing;

    // ---- Quantities ----

    @Column(precision = 15, scale = 5)
    private BigDecimal plannedQuantity;

    @Column(precision = 15, scale = 5)
    private BigDecimal completedQuantity = BigDecimal.ZERO;

    @Column(precision = 15, scale = 5)
    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private WorkOrderStatus status;

    /**
     * Which sales-order line this line satisfies, when the work order came from a sales order.
     * Needed so finished-goods reservation can target the right SO line rather than the SO header.
     */
    private Long salesOrderItemId;

    /** Optional per-line override of the work order's due date. */
    private Date dueDate;

    // ---- Estimation (per line; the header value is the sum across lines) ----

    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedProductionMinutes;

    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedTotalCost;

    /** Provenance when this line was produced by a split. Distinct from WorkOrder.parentWorkOrder. */
    private Long splitFromLineId;

    // ---- Children (re-parented from WorkOrder) ----

    @OneToMany(mappedBy = "workOrderLine", fetch = FetchType.LAZY)
    private List<WorkOrderMaterial> materials = new ArrayList<>();

    @OneToMany(mappedBy = "workOrderLine", fetch = FetchType.LAZY)
    private List<WorkOrderOperation> operations = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    // ─── Yield metrics (computed, not persisted) ──────────────────────────────
    // Moved here from WorkOrder: dividing by a header quantity that spans unlike items and
    // units of measure produces a meaningless figure.

    /**
     * Good units this line produced: the output of its LAST operation.
     *
     * <p>Not a sum across operations. One unit routed through Assembly and then Testing is
     * completed once by each, and adding those reports two units made from one — a line planned
     * for 1 with a two-operation routing showed 200% yield. Intermediate operations can also
     * differ from each other through scrap and rejection; only what leaves the final operation
     * is finished output. This is the same rule the completion quantity uses.
     */
    public BigDecimal getTotalOperationGoodQuantity() {
        if (operations == null) return BigDecimal.ZERO;
        return operations.stream()
                .filter(op -> op.getDeletedDate() == null)
                .max(java.util.Comparator.comparingInt(WorkOrderOperation::getSequence))
                .map(WorkOrderOperation::getCompletedQuantity)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Rejections and scrap ARE summed across operations, unlike good quantity above: a unit lost
     * at Assembly and another lost at Testing are two separate losses, not the same unit counted
     * twice.
     */
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
        return asPercentOfPlanned(getTotalOperationGoodQuantity());
    }

    public BigDecimal getReworkRate() {
        return asPercentOfPlanned(getTotalOperationRejectedQuantity());
    }

    public BigDecimal getScrapRate() {
        return asPercentOfPlanned(getTotalOperationScrapQuantity());
    }

    public BigDecimal getOverallYield() {
        return asPercentOfPlanned(
                getTotalOperationGoodQuantity().add(getTotalOperationRejectedQuantity()));
    }

    private BigDecimal asPercentOfPlanned(BigDecimal value) {
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value
                .divide(plannedQuantity, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
