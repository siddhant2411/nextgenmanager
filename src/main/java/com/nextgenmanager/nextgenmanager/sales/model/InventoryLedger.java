package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryLedger")
public class InventoryLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private LocalDate movementDate;

    private String transactionType; // DELIVERY_NOTE, SALES_RETURN, PURCHASE_RECEIPT, etc.

    private int quantity;           // Total quantity moved
    private int closingBalance;     // After this movement

    @ManyToOne(fetch = FetchType.LAZY)
    private InventoryItem inventoryItem;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_ledger_id")
    private List<InventoryInstance> inventoryInstances; // ✅ Track specific instances involved

    private String referenceDocNo; // E.g., DN0012 or PO0023
}
