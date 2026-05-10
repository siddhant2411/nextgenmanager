package com.nextgenmanager.nextgenmanager.purchase.requisition.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "purchaseRequisitionItem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id")
    private PurchaseRequisition requisition;

    @Column(nullable = false)
    private Integer lineNumber = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_inventoryitemid")
    private InventoryItem item;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String uom;

    private double quantity;

    private Date requiredByDate;

    /** Optional vendor suggestion (e.g. preferred vendor from ItemVendorPrice). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_vendor_id")
    private Contact suggestedVendor;

    @Column(precision = 14, scale = 4)
    private BigDecimal estimatedUnitPrice = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal estimatedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequisitionItemStatus lineStatus = PurchaseRequisitionItemStatus.PENDING;

    /** Optional link back to the WorkOrderMaterial line that triggered this row. */
    private Long workOrderMaterialId;

    /** Set when this line is converted to a Purchase Order. */
    private Long purchaseOrderId;

    private String notes;
}
