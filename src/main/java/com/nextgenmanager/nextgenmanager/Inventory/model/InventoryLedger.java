package com.nextgenmanager.nextgenmanager.Inventory.model;

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

    private String transactionType; // GRN, WO_ISSUE, WO_CONSUME, ADJUSTMENT, RETURN, etc.

    private double quantity;
    private double closingBalance;

    private double rate;
    private double amount;

    /** FIFO, AVERAGE, STANDARD — defaults to AVERAGE */
    private String valuationMethod;

    private String warehouse;

    /** Type of the source document — e.g. GRN, WORK_ORDER, SALES_ORDER */
    private String referenceType;

    private String referenceDocNo;

    private String createdBy;

    private String overrideReason;

    private double scrappedQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    private InventoryItem inventoryItem;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_ledger_id")
    private List<InventoryInstance> inventoryInstances;
}
