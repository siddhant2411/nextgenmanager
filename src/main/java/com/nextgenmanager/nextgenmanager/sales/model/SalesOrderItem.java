package com.nextgenmanager.nextgenmanager.sales.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "salesOrderItem")
public class SalesOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_item_id") // this foreign key should exist in InventoryInstance
    private List<InventoryInstance> inventoryInstanceList;

    @Column(length = 20, nullable = false)
    private String hsnCode;                   // mandatory for GST


    @Column(precision = 10, scale = 2) private BigDecimal qty;
    @Column(precision = 12, scale = 2) private BigDecimal pricePerUnit;
    @Column(precision = 5,  scale = 2) private BigDecimal discountPercentage;
    @Column(precision = 12,  scale = 2) private BigDecimal unitPriceAfterDiscount;
    @Column(precision = 12, scale = 2) private BigDecimal totalAmountOfProduct;

//    It will be mapped to InventoryRequestId Directly on run time to check status
    private Long itemRequestId;
}
