package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Job Work Challan — the dispatch document issued when materials / semi-finished goods
 * are sent to a job worker (subcontractor) for processing.
 *
 * Legal basis: CGST Act Section 143 and GST Rule 45.
 * - Materials must be returned within 180 days (1 year for capital goods).
 * - If not returned, the principal must declare them as a supply and pay GST.
 *
 * One challan covers one job-worker visit. Multiple challans can exist per Work Order.
 */
@Entity
@Table(name = "jobWorkChallan", indexes = {
    @Index(name = "idx_challan_vendor",    columnList = "vendorId"),
    @Index(name = "idx_challan_workorder", columnList = "workOrderId"),
    @Index(name = "idx_challan_status",    columnList = "status"),
    @Index(name = "idx_challan_number",    columnList = "challanNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobWorkChallan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable challan reference.
     * Auto-generated as JWC/FY/NNNN  (e.g., JWC/2025-26/0001).
     * Unique and immutable after creation.
     */
    @Column(nullable = false, length = 30, unique = true, updatable = false)
    private String challanNumber;

    /** Job Worker / Subcontractor — must be a VENDOR or BOTH contact. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendorId", nullable = false)
    private Contact vendor;

    /** Optional link to the triggering Work Order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderId")
    private WorkOrder workOrder;

    /**
     * Optional link to the specific SUB_CONTRACTED WorkOrderOperation.
     * Helps track which routing step this challan serves.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderOperationId")
    private WorkOrderOperation workOrderOperation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ChallanStatus status = ChallanStatus.DRAFT;

    /** Date materials were physically dispatched. Set when status → DISPATCHED. */
    private Date dispatchDate;

    /**
     * Statutory deadline for return. Default = dispatchDate + 180 days.
     * For capital goods, this can be extended to 1 year.
     */
    private Date expectedReturnDate;

    /** Set when the last receipt closes the challan. */
    private Date actualReturnDate;

    /**
     * Agreed job-work rate per unit (for reference / future PO linkage).
     * Actual cost is captured per line.
     */
    @Column(precision = 14, scale = 4)
    private BigDecimal agreedRatePerUnit;

    /** Shipping / transport details for the dispatch. */
    @Column(length = 200)
    private String dispatchDetails;

    private String remarks;

    @OneToMany(mappedBy = "challan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobWorkChallanLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
