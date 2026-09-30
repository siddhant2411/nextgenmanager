package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The physical act of taking goods off a shelf for a sales order.
 *
 * <p>Records what dispatch previously left implicit: who picked, from which warehouse and
 * location, and which specific units left. Picking does not reserve — stock is reserved at
 * sales-order approval — it chooses which instances satisfy that reservation.
 */
@Entity
@Table(name = "picklist")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PickList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String pickNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salesOrder_id", nullable = false)
    private SalesOrder salesOrder;

    /** One warehouse per pick: an order spanning two warehouses is two physical trips. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PickListStatus status = PickListStatus.DRAFT;

    /**
     * The delivery note that consumed this pick. Set once, at dispatch — a pick that has one is
     * spent, which is what stops a second delivery note taking the same units off the shelf.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deliverynote_id")
    private DeliveryNote deliveryNote;

    @Temporal(TemporalType.TIMESTAMP) private Date releasedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date pickedDate;

    @Column(length = 100) private String pickedBy;
    @Column(length = 500) private String remarks;
    @Column(length = 100) private String createdBy;

    @OneToMany(mappedBy = "pickList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PickListLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date deletedDate;
}
