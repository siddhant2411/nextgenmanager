package com.nextgenmanager.nextgenmanager.items.model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "productFinanceSettings")
public class ProductFinanceSettings {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private Double standardCost;

    private Double lastPurchaseCost ;

    private Double sellingPrice;

    private String costingMethod;

    private Double profitMargin;

    private Double minimumSellingPrice;

    private String taxCategory;

    private String currency;

    private Date lastUpdatedOn;

    private Date expiryDate;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryitemid", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private InventoryItem inventoryItem;


}
