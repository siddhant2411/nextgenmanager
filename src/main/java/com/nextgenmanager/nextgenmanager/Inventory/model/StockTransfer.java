package com.nextgenmanager.nextgenmanager.Inventory.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Moves stock from one warehouse to another, in two steps.
 *
 * <p>Dispatch and receipt are separate because the gap between them is real: goods on a vehicle
 * have left one store and not yet arrived at the other. During that window the quantity is
 * counted as {@code inTransit} at the source, which keeps the V165 invariant — company total
 * equals the sum of the per-warehouse rows — exactly true rather than true only when nothing
 * is moving.
 */
@Entity
@Table(name = "stocktransfer")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fromWarehouse_id", nullable = false)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "toWarehouse_id", nullable = false)
    private Warehouse toWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockTransferStatus status = StockTransferStatus.DRAFT;

    @Temporal(TemporalType.TIMESTAMP) private Date dispatchedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date receivedDate;

    @Column(length = 500) private String remarks;
    @Column(length = 100) private String createdBy;

    @OneToMany(mappedBy = "stockTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockTransferLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date deletedDate;
}
