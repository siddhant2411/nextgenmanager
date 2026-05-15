package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxinvoice_id")
    private TaxInvoice taxInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryitem_id")
    private InventoryItem inventoryItem;

    @Column(precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(precision = 12, scale = 2) private BigDecimal cgstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal sgstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal igstAmount;
    @Column(precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(length = 1000 , name = "serial_numbers") private String serialNumbers;
}
