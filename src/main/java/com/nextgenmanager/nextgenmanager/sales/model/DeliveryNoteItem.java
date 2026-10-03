package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "deliveryNoteItem")
public class DeliveryNoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;

    /**
     * Decimal since V176. It was a whole number, which meant a pick of 2.5 kg could not be shipped
     * at all — the choice was to refuse it or to truncate it, and truncating loses stock silently.
     */
    @Column(precision = 18, scale = 4, nullable = false)
    private java.math.BigDecimal quantityDelivered = java.math.BigDecimal.ZERO;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_note_item_id")
    private List<InventoryInstance> inventoryInstanceList;

    @Column(precision = 15, scale = 5)
    private java.math.BigDecimal actualCost;


    @ManyToOne
    @JoinColumn(name = "delivery_note_id")
    private DeliveryNote deliveryNote;
}
