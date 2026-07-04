package com.nextgenmanager.nextgenmanager.bom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A flat, unmeasured cost line on a BOM — for CONSUMABLE-type items where there is no honest
 * per-unit quantity (e.g. grease in a gear assembly).
 *
 * <p>Unlike {@link BomPosition}, a cost line carries a fixed {@code amount} (a per-BOM price,
 * not qty × rate) and is <b>never</b> exploded into work-order materials, issued, or posted to
 * the GL — it is a costing-only element that rolls into the BOM's estimated standard cost. It
 * references an existing {@link InventoryItem} (of ItemType CONSUMABLE) created in the Product
 * Master; the BOM never creates items.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bomCostLine")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BomCostLine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentBomId", nullable = false)
    private Bom parentBom;

    /** The existing master item this cost line represents (required). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    /** Flat price for this item on this BOM (entered per BOM). */
    private BigDecimal amount;

    @Column(name = "position")
    private int position;
}
