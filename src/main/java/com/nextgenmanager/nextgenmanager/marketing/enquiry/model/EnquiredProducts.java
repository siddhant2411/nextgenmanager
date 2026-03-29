package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "enquiredProducts")
public class EnquiredProducts {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = true)
    private InventoryItem inventoryItem;

    private String productNameRequired;

    @Column(precision = 10, scale = 2)
    private BigDecimal qty = BigDecimal.ZERO;

    private String specialInstruction;

    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerUnit = BigDecimal.ZERO;


    @ManyToOne
    @JoinColumn(name = "enquiry_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Enquiry enquiry;

}
