package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "picklistline")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PickListLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "picklist_id", nullable = false)
    private PickList pickList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesOrderItem_id")
    private SalesOrderItem salesOrderItem;

    /** Where to go. Null unless the warehouse is bin-tracked, which is off by default. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storagelocation_id")
    private StorageLocation storageLocation;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantityToPick;

    /** What the picker actually found. Less than asked is a short pick, kept rather than hidden. */
    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantityPicked = BigDecimal.ZERO;

    @Column(length = 300)
    private String remarks;

    /** The specific units taken off the shelf for this line. */
    @OneToMany(mappedBy = "pickListLine")
    private List<InventoryInstance> allocatedInstances = new ArrayList<>();
}
