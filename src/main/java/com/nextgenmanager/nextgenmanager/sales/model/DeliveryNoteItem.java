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
    private InventoryItem inventoryItem;

    private int quantityDelivered;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_note_item_id")
    private List<InventoryInstance> inventoryInstanceList;


    @ManyToOne
    private DeliveryNote deliveryNote;
}
