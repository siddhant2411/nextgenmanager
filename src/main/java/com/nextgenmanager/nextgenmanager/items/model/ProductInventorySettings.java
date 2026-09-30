package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "productInventorySettings")
public class ProductInventorySettings {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private double reorderLevel;

    private double minStock;

    private double maxStock;

    private double leadTime;

    private boolean isBatchTracked;

    private boolean isSerialTracked;

    private boolean purchased;

    private boolean manufactured;

    /**
     * Replenishment route for this item. Drives how a sales-order shortfall is handled:
     * MAKE_TO_STOCK reserves stock and relies on reorder rules; MAKE_TO_ORDER raises a
     * procurement need chained to the order. Defaults to MAKE_TO_STOCK to preserve
     * existing reserve-from-stock behaviour.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReplenishmentStrategy replenishmentStrategy = ReplenishmentStrategy.MAKE_TO_STOCK;

    private double availableQuantity;

    private double orderedQuantity;

    /**
     * Quantity committed to active orders (WO / SO). Already removed from availableQuantity.
     * Total physical stock on hand = availableQuantity + reservedQuantity.
     */
    private double reservedQuantity;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private InventoryItem inventoryItem;

    private boolean allowNegativeStock = false;

    /**
     * Whether finished goods of this item may be produced without a passed final inspection.
     *
     * <p>Off by default, and deliberately so: every item in this database has been produced
     * without an inspection lot for its whole life, and requiring one everywhere at once would
     * stop a shop floor that has never raised one. A lot that exists and failed blocks production
     * whatever this says — that rule needs no configuration.
     */
    private boolean finalInspectionRequired = false;

    @PrePersist
    @PreUpdate
    private void validateTrackingConfig() {
        if (this.isBatchTracked || this.isSerialTracked) {
            this.allowNegativeStock = false;
        }
    }
}
