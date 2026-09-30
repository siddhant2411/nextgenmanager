package com.nextgenmanager.nextgenmanager.packaging.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListLine;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** What went into one box: an item, a quantity, and — where it came from a pick — which line. */
@Entity
@Table(name = "packageline")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PackageLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "packagebox_id", nullable = false)
    private PackageBox packageBox;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    /** Which pick line this came from. Null for goods boxed outside a pick. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picklistline_id")
    private PickListLine pickListLine;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    /** The specific units packed into this line's box. */
    @OneToMany(mappedBy = "packageLine")
    private List<InventoryInstance> allocatedInstances = new ArrayList<>();
}
