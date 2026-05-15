package com.nextgenmanager.nextgenmanager.Inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks the "Biography" of a specific InventoryInstance.
 * Records every hand-off, status change, and location movement.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "inventoryMovementLog")
public class InventoryMovementLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryInstanceId", nullable = false)
    private InventoryInstance inventoryInstance;

    private String fromLocation;
    private String toLocation;

    /** E.g. RECEIVE, QC_UPDATE, ALLOCATE, CONSUME, TRANSFER, DISPATCH */
    private String transactionType;

    /** Reference document like GRN-2025-001, WO-500, SO-200 */
    private String referenceNumber;

    private String actionBy;

    @Column(length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
