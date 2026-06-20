package com.nextgenmanager.nextgenmanager.purchase.model;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "vendorInvoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VendorInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String invoiceNumber;

    private LocalDate invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Contact vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_id")
    private GoodsReceiptNote grn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorInvoiceStatus status = VendorInvoiceStatus.DRAFT;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    private boolean qtyMismatch;
    private boolean amountMismatch;

    // ── GST / ITC (Phase 2) ──────────────────────────────────────────────────
    /** Whether the input tax on this bill is claimable as ITC (false = blocked credit, Sec 17(5)). */
    @Column(nullable = false)
    private boolean itcEligible = true;

    /** Reason ITC is ineligible (free text) — populated only when {@link #itcEligible} is false. */
    @Column(length = 120)
    private String itcIneligibleReason;

    /** Whether tax on this bill is payable under reverse charge (RCM) by the recipient. */
    @Column(nullable = false)
    private boolean reverseCharge = false;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VendorInvoiceItem> items;
}
