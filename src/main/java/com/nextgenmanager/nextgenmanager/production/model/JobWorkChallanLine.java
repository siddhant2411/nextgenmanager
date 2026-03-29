package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One material line on a Job Work Challan.
 * Tracks dispatched quantity and quantity received back.
 */
@Entity
@Table(name = "jobWorkChallanLine")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobWorkChallanLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challanId", nullable = false)
    private JobWorkChallan challan;

    /**
     * Material being sent to the job worker.
     * Can be raw material, component, or semi-finished WIP.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemId")
    private InventoryItem item;

    /** Free-text description (used when item is not in master, e.g., customer-supplied). */
    @Column(length = 200)
    private String description;

    /** HSN/SAC code required on GST challan. Copied from item master if available. */
    @Column(length = 10)
    private String hsnCode;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal quantityDispatched;

    /** Received back after job work — updated on each receipt. */
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    /** Rejected by QC on return — counts against the 180-day closure. */
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal quantityRejected = BigDecimal.ZERO;

    @Column(length = 20)
    private String uom;

    /**
     * Declared value per unit for GST challan valuation.
     * Required to calculate applicable taxes if material is not returned.
     */
    @Column(precision = 14, scale = 4)
    private BigDecimal valuePerUnit;

    private String remarks;

    private Date lastReceiptDate;
}
