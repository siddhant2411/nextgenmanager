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

    private double availableQuantity;

    private double orderedQuantity;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private InventoryItem inventoryItem;
}
