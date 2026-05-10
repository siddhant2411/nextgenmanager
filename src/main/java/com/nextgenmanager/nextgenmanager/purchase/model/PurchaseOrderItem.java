package com.nextgenmanager.nextgenmanager.purchase.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "purchaseOrderItem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchaseOrder_id")
    private PurchaseOrder purchaseOrder;

    /** Display order within the PO. */
    @Column(nullable = false)
    private Integer lineNumber = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_inventoryitemid")
    private InventoryItem item;

    /** Custom description — defaults to item name if blank. */
    @Column(length = 500)
    private String description;

    /** Snapshot of item HSN at PO time. */
    @Column(length = 10)
    private String hsnCode;

    /** Snapshot of item UOM at PO time. */
    @Column(length = 20)
    private String uom;

    private double quantityOrdered;
    private double quantityReceived;

    @Column(precision = 14, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** (qty × unitPrice) − discountAmount */
    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal taxableValue = BigDecimal.ZERO;

    /** Snapshot of GST rate % at PO time. */
    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal gstRatePct = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    /** taxableValue + all taxes. */
    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    private Date requiredByDate;

    /** Optional: links back to the SO line that triggered this purchase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesOrderItemId")
    private SalesOrderItem salesOrderItem;

    @OneToMany
    @JoinColumn(name = "purchase_order_item_id")
    private List<InventoryInstance> receivedInstances;

    private String remarks;
}
