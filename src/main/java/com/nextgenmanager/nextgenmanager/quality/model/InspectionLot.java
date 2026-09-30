package com.nextgenmanager.nextgenmanager.quality.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * One inspection and its verdict: this quantity of this item, arising from this document, was
 * looked at, and here is the answer.
 *
 * <p>The point of the lot is that something downstream refuses to ignore it. Measurement was never
 * the gap — {@code BomQaParameter}, {@code WorkOrderQaEntry} and {@code WorkOrderQaResult} have
 * recorded in-process inspection in detail for a long time, and a work order whose critical
 * parameter failed completed anyway. The lot is the verdict a movement can be gated on.
 */
@Entity
@Table(name = "inspectionlot")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class InspectionLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String lotNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionLotStatus status = InspectionLotStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    // Exactly one of the three is set, decided by the source. Separate typed columns rather than
    // a polymorphic id, so the database still knows the document being inspected exists.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goodsReceiptNote_id")
    private GoodsReceiptNote goodsReceiptNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrder_id")
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderOperation_id")
    private WorkOrderOperation workOrderOperation;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantityOffered;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantityAccepted = BigDecimal.ZERO;

    /**
     * What failed inspection. Offered minus accepted is deliberately not assumed to be rejected:
     * a part-inspected lot has units that are neither yet.
     */
    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantityRejected = BigDecimal.ZERO;

    @Column(length = 100) private String inspectedBy;
    @Temporal(TemporalType.TIMESTAMP) private Date inspectedDate;

    /** A waiver is a decision someone owns, so it is recorded rather than inferred. */
    @Column(length = 100) private String waivedBy;
    @Column(length = 500) private String waiverReason;

    @Column(length = 500) private String remarks;
    @Column(length = 100) private String createdBy;

    @OneToMany(mappedBy = "inspectionLot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionResult> results = new ArrayList<>();

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date deletedDate;

    /** Whether this lot lets the movement it gates go ahead. */
    public boolean clearsTheGate() {
        return status == InspectionLotStatus.PASSED || status == InspectionLotStatus.WAIVED;
    }
}
