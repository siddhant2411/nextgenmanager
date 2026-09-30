package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * How much of one item sits in one warehouse.
 *
 * <p>The company-wide totals on {@code ProductInventorySettings} remain the rollup, and the sum of
 * these rows for an item must equal them. That invariant is the whole point: an earlier pair of
 * counters had no way to be checked against anything, drifted, and had to be repaired by hand.
 * This one is checkable for every item — tracked or not — and the stock reconciliation report
 * verifies it.
 */
@Entity
@Table(name = "itemwarehousestock", indexes = {
        @Index(name = "idx_itemwarehousestock_warehouse", columnList = "warehouse_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ItemWarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Free to allocate in this warehouse. */
    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal onHand = BigDecimal.ZERO;

    /** Spoken for in this warehouse but not yet consumed. */
    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal reserved = BigDecimal.ZERO;

    /**
     * Dispatched from here on a transfer but not yet received anywhere. Still owned and still
     * inside the company-wide total, which is why the invariant is
     * {@code availableQuantity == SUM(onHand) + SUM(inTransit)} rather than onHand alone.
     */
    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal inTransit = BigDecimal.ZERO;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;
}
