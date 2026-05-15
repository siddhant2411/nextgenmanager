package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.Date;

@Entity
@Table(name = "serialNumber", indexes = {
        @Index(name = "idx_serial_item",   columnList = "inventoryItemRef"),
        @Index(name = "idx_serial_status", columnList = "status"),
        @Index(name = "idx_serial_batch",  columnList = "batchId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SerialNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    /** Optional — serial may belong to a batch (e.g., 10 serials from one GRN batch) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batchId")
    private BatchNumber batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SerialStatus status = SerialStatus.AVAILABLE;

    private LocalDate receivedDate;

    /** GRN | WORK_ORDER | MANUAL */
    @Column(nullable = false)
    private String source;

    /** GRN number or Work Order number that created this serial */
    private String sourceDocNo;

    private String warehouse;

    private Date consumedDate;

    /** WO number or SO number that consumed this serial */
    private String consumedByDocNo;

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
