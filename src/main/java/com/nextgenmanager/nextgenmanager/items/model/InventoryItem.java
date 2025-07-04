package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryItem")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int InventoryItemId;

    @Column(unique = true, nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    private String hsnCode;

    @Column(nullable = false)
    private UOM uom;


    @Column(nullable = false)
    private ItemType itemType;

    private String dimension;

    private String size;

    private String weight;

    private byte revision;

    private String remarks;

    private String basicMaterial;


    private String processType;

    private String leadTime;

    private Double standardCost;

    private Double sellingPrice;

    private String reorderLevel;

    private String minStock;

    private String maxStock;

    private String taxCategory;

    private boolean isBatchTracked;

    private boolean isSerialTracked;

    private boolean purchased;

    private boolean manufactured;

    @Column(nullable = true)
    private String itemGroupCode;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    private double availableQuantity;

    private double orderedQuantity;

    @OneToMany(mappedBy = "inventoryItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InventoryItemAttachment> inventoryItemAttachmentList;
}