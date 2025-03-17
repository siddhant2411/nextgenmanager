package com.nextgenmanager.nextgenmanager.marketing.quotation.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
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
@Table(name = "quotationProducts")
public class QuotationProducts {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = true)
    private InventoryItem inventoryItem;

    private String productNameRequired;

    private double pricePerUnit;

    private double qty;

    private double discountPercentage;

    private String specialInstruction;

    private BigDecimal unitPriceAfterDiscount;

    private BigDecimal  totalAmountOfProduct;

    @ManyToOne
    @JoinColumn(name = "quotation_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Quotation quotation;

    @PrePersist
    public void prePersist() {
        if (discountPercentage == 0) {
            discountPercentage = 0;
        }
    }

}
