package com.nextgenmanager.nextgenmanager.items.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Records a vendor's quoted price for an inventory item.
 *
 * One item can have many vendor prices — multiple suppliers for PURCHASE,
 * multiple job-workers for JOB_WORK. The preferred vendor's price feeds
 * the Make-or-Buy analysis automatically.
 */
@Entity
@Table(
    name = "itemVendorPrice",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_item_vendor_pricetype",
        columnNames = {"inventoryItemId", "vendorId", "priceType"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemVendorPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendorId", nullable = false)
    private Contact vendor;

    /**
     * PURCHASE — vendor supplies the item.
     * JOB_WORK — vendor processes your material (subcontract / job work).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceType priceType;

    /** Price per unit in INR (or currency below). */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal pricePerUnit;

    @Column(length = 3)
    private String currency = "INR";

    /** Supplier lead time in days. */
    private Integer leadTimeDays;

    /** Minimum order / job-work quantity. */
    @Column(precision = 12, scale = 3)
    private BigDecimal minimumOrderQuantity;

    /** Price validity window. */
    private Date validFrom;
    private Date validTo;

    /** Date when this price was last confirmed / re-quoted by the vendor. */
    private Date lastQuotedDate;

    /**
     * When true, this vendor's price is used in Make-or-Buy analysis
     * for the corresponding priceType. Only one preferred vendor per
     * (item + priceType) combination should be true at a time
     * — enforced at service layer.
     */
    @Column(nullable = false)
    private boolean isPreferredVendor = false;

    /**
     * Whether this vendor is GST-registered.
     * GST-registered suppliers allow Input Tax Credit (ITC) recovery,
     * which lowers the effective buy cost — important for Indian MSME.
     */
    @Column(nullable = false)
    private boolean gstRegistered = true;

    /** Payment terms, e.g. "30 days net", "Advance", "LC". */
    @Column(length = 100)
    private String paymentTerms;

    private String remarks;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
