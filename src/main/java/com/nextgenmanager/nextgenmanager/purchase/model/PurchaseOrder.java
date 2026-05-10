package com.nextgenmanager.nextgenmanager.purchase.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "purchaseOrder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String purchaseOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Contact vendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderType poType = PurchaseOrderType.STANDARD;

    private Date orderDate;
    private Date expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderApprovalStatus approvalStatus = PurchaseOrderApprovalStatus.DRAFT;

    /** Derived from vendor vs company GSTIN state codes and stored for audit. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GstTreatment gstTreatment;

    /** 2-digit GST state code of the delivery destination. */
    @Column(length = 2)
    private String placeOfSupply;

    /** Vendor's billing address snapshot selected at PO time. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendorBillingAddressId")
    private ContactAddress vendorBillingAddress;

    /** Our warehouse / delivery address. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipToAddressId")
    private ContactAddress shipToAddress;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Column(precision = 18, scale = 6, nullable = false)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** Snapshotted from vendor's default at PO creation; can be overridden. */
    @Column(length = 100)
    private String paymentTerms;

    private Integer creditDays;

    // ── Totals ───────────────────────────────────────────────────────────────

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal taxableValue = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    @Column(precision = 6, scale = 2, nullable = false)
    private BigDecimal roundOff = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    // ── Approval ─────────────────────────────────────────────────────────────

    private String approvedBy;
    private Date approvedDate;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Versioning / Amendments ───────────────────────────────────────────────

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @Column(nullable = false)
    private Integer revisionNo = 0;

    /** Set when this PO is a revision of an earlier PO. */
    private Long parentPoId;

    // ── Vendor Quotation Reference ────────────────────────────────────────────

    /** Vendor's quotation/offer number that this PO is raised against. */
    @Column(length = 100)
    private String quotationNumber;

    private Date quotationDate;

    // ── Email Tracking ────────────────────────────────────────────────────────

    /** Timestamp of when this PO was last emailed to the vendor. */
    private Date sentToVendorAt;

    @Column(length = 255)
    private String sentToVendorEmail;

    // ── Free text ─────────────────────────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(columnDefinition = "TEXT")
    private String internalNotes;

    private String remarks;

    // ── Relationships ─────────────────────────────────────────────────────────

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private SalesOrder salesOrder;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<PurchaseOrderItem> items;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
