package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "salesCreditNoteItem")
public class SalesCreditNoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_credit_note_id", nullable = false)
    private SalesCreditNote salesCreditNote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    private int lineNumber;

    private double returnedQty;
    private double rate;
    private double gstRate;
    private double gstAmount;
    private double totalAmount;

    private String warehouseTo;
    private String remarks;
}
