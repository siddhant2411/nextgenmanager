package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
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
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private InventoryItem inventoryItem;

    private int quantity;

    @ManyToOne
    private TaxInvoice taxInvoice;
}
