package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.BatchNumber;
import com.nextgenmanager.nextgenmanager.Inventory.model.SerialNumber;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "inventoryInstance",indexes = {
        @Index(name = "idx_invinst_itemref", columnList = "inventoryItemRef"),
        @Index(name = "idx_invinst_booked_date", columnList = "bookedDate"),
        @Index(name = "idx_invinst_deleted_date", columnList = "deletedDate"),
        @Index(name = "idx_invinst_is_consumed", columnList = "isConsumed"),
        @Index(name = "idx_invinst_filter_combo", columnList = "inventoryItemRef, bookedDate, deletedDate")
})

public class InventoryInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uniqueId = generateShortUUID();

    @ManyToOne
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Prevents serialization issues
    private InventoryItem inventoryItem;

    @Column(precision = 15, scale = 5)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column
    private boolean isConsumed=false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date entryDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date consumeDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bookedDate")
    private Date bookedDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date deliveryDate;


    @Column(precision = 12, scale = 2)
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal sellPricePerUnit = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryRequestId")
    @JsonBackReference
    private InventoryRequest inventoryRequest;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    @Enumerated(EnumType.STRING)
    private InventoryInstanceStatus inventoryInstanceStatus = InventoryInstanceStatus.PENDING;

    private String consumptionReferenceNo; // Tracks the document (e.g., Work Order) that consumed this instance

    /**
     * Which warehouse this physical stock is in. NOT NULL since V165 — stock always sits
     * somewhere, and a nullable column here would make per-warehouse totals quietly under-report.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /**
     * Whether this particular stock is fit to use. PASSED and WAIVED can be picked; PENDING_QC and
     * FAILED cannot.
     *
     * <p>Until phase H this lived only on {@code BatchNumber} and {@code SerialNumber}, so
     * untracked stock had no way to say it had failed — and a goods receipt's rejected quantity
     * was simply dropped rather than being held anywhere.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "qualityStatus", nullable = false, length = 20)
    private QualityStatus qualityStatus = QualityStatus.PASSED;

    /**
     * The pick that allocated this unit, if any. Cleared when a pick is cancelled so allocation is
     * never stranded on stock that is back on the shelf.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picklistline_id")
    private PickListLine pickListLine;

    /** Bin within the warehouse. Null unless that warehouse is bin-tracked, which is off by default. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storagelocation_id")
    private StorageLocation storageLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batchId")
    private BatchNumber batchNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serialId", unique = true)
    private SerialNumber serialNumber;

    @Column(name = "sales_order_item_id")
    private Long salesOrderItemId;

    @Column(name = "delivery_note_item_id")
    private Long deliveryNoteItemId;

    private static String generateShortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}