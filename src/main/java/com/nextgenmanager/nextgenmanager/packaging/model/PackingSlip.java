package com.nextgenmanager.nextgenmanager.packaging.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * What went into boxes for one confirmed pick.
 *
 * <p>One slip per pick — the same one-trip reasoning that gives a pick list one warehouse. The
 * slip does not allocate anything itself; it records the boxes built from stock the pick already
 * set aside.
 */
@Entity
@Table(name = "packingslip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PackingSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String slipNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salesOrder_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "picklist_id", nullable = false)
    private PickList pickList;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PackingSlipStatus status = PackingSlipStatus.DRAFT;

    /**
     * The delivery note this slip shipped on. Set once, at dispatch. Null on a closed slip means
     * packed and waiting for a lorry.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deliverynote_id")
    private DeliveryNote deliveryNote;

    @Temporal(TemporalType.TIMESTAMP) private Date packedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date closedDate;

    @Column(length = 100) private String packedBy;
    @Column(length = 100) private String closedBy;
    @Column(length = 500) private String remarks;
    @Column(length = 100) private String createdBy;

    @OneToMany(mappedBy = "packingSlip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackageBox> boxes = new ArrayList<>();

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date deletedDate;
}
