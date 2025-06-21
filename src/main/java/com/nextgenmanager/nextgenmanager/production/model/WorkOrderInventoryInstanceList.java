package com.nextgenmanager.nextgenmanager.production.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderBomList")
public class WorkOrderInventoryInstanceList {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private List<InventoryInstance> inventoryInstanceList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId",nullable = true) // Foreign key mapping
    private InventoryItem  inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id", referencedColumnName = "id",nullable = true) // Foreign key mapping
    private Bom bom;

    @Enumerated(EnumType.STRING)
    private InventoryStatus inventoryStatus;

    private Date expectedDeliveryDate;

    private String remarks;


    @ManyToOne
    @JoinColumn(name = "work_order_production_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private WorkOrderProduction workOrderProduction;

}
