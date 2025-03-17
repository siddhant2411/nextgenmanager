package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "enquiredProducts")
public class EnquiredProducts {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = true)
    private InventoryItem inventoryItem;

    private String productNameRequired;

    private double qty;

    private String specialInstruction;


    private Double pricePerUnit = 0.0;


    @ManyToOne
    @JoinColumn(name = "enquiry_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Enquiry enquiry;

}
