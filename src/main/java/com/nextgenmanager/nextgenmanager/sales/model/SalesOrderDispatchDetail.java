package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
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
@Table(name = "salesOrderDispatchDetail")
public class SalesOrderDispatchDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sales_order_item_id")
    private SalesOrderItem salesOrderItem;

    @ManyToOne
    @JoinColumn(name = "inventory_instance_id")
    private InventoryInstance inventoryInstance;

    private double dispatchedQuantity;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dispatchDate;

    private String deliveryReference;
}
