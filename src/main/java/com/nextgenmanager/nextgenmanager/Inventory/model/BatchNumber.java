package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Entity
@Table(name = "batchNumber", indexes = {
        @Index(name = "idx_batch_item",   columnList = "inventoryItemRef"),
        @Index(name = "idx_batch_status", columnList = "status"),
        @Index(name = "idx_batch_expiry", columnList = "expiryDate")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BatchNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    private LocalDate manufacturingDate;

    private LocalDate expiryDate;

    /** Supplier's own batch/lot number printed on the packaging (GRN only) */
    private String supplierBatchNo;

    @Column(precision = 15, scale = 5, nullable = false)
    private BigDecimal originalQty;

    @Column(precision = 15, scale = 5, nullable = false)
    private BigDecimal remainingQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.ACTIVE;

    /** GRN | WORK_ORDER | MANUAL */
    @Column(nullable = false)
    private String source;

    /** GRN number or Work Order number that created this batch */
    private String sourceDocNo;

    private String warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QualityStatus qualityStatus = QualityStatus.PENDING_QC;

    @Column(length = 500)
    private String qualityRemarks;

    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;
}
