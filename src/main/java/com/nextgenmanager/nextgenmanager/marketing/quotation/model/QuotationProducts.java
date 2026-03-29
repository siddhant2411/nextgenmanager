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
@Table(name = "quotation_products")
public class QuotationProducts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = true)
    private InventoryItem inventoryItem;

    private String productNameRequired;

    @Column(precision = 10, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    private String specialInstruction;

    @Column(precision = 10, scale = 2)
    private BigDecimal unitPriceAfterDiscount;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmountOfProduct;

    @ManyToOne
    @JoinColumn(name = "quotation_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Quotation quotation;

    @PrePersist
    public void prePersist() {
        if (discountPercentage == null) {
            discountPercentage = BigDecimal.ZERO;
        }
    }
}
