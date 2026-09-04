package com.nextgenmanager.nextgenmanager.Inventory.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * A physical place stock sits.
 *
 * <p>Before this existed, "warehouse" was a free-text string repeated across the ledger, GRNs,
 * batches and serials — nothing constrained the spelling and there was no way to enumerate
 * warehouses, transfer between them, or route a rejected receipt out of usable stock.
 */
@Entity
@Table(name = "warehouse", indexes = {
        @Index(name = "idx_warehouse_type",   columnList = "warehouseType"),
        @Index(name = "idx_warehouse_active", columnList = "active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human handle printed on documents. Unique among live rows; a retired code may be reused. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarehouseType warehouseType = WarehouseType.GENERAL;

    @Column(length = 200) private String addressLine1;
    @Column(length = 200) private String addressLine2;
    @Column(length = 100) private String city;
    @Column(length = 100) private String state;
    @Column(length = 10)  private String pincode;

    /** Only set where this warehouse files under a different GSTIN; null means the company one. */
    @Column(length = 15)
    private String gstin;

    /**
     * Exactly one warehouse is the default. A partial unique index enforces that in the
     * database, so no service code has to police it.
     */
    @Column(nullable = false)
    private boolean isDefault = false;

    /**
     * When false this warehouse is a single bucket and {@link StorageLocation} rows are not
     * required. Off by default — the location level is modelled so it need not be retrofitted,
     * not because every site wants bins.
     */
    @Column(nullable = false)
    private boolean binTracked = false;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date deletedDate;
}
