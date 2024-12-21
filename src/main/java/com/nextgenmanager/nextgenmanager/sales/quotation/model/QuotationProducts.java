package com.nextgenmanager.nextgenmanager.sales.quotation.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
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

    @ManyToOne(fetch = FetchType.LAZY) // Lazy load the associated Contact
    @JoinColumn(name = "inventory_instance_id", referencedColumnName = "id") // Foreign key mapping
    private InventoryInstance inventoryInstance;

    private double qty;

    private double discountPercentage;

    private String description;

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
