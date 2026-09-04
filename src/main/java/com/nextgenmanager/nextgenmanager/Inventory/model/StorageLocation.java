package com.nextgenmanager.nextgenmanager.Inventory.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * A bin within a warehouse — the level a picker actually walks to.
 *
 * <p>Only meaningful for warehouses with {@code binTracked} on, which is off by default. The
 * table exists from the start so the location level never has to be retrofitted into every
 * stock query later.
 */
@Entity
@Table(name = "storagelocation", indexes = {
        @Index(name = "idx_storagelocation_warehouse", columnList = "warehouse_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StorageLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Short label printed on a pick list, e.g. {@code A-03-2}. Unique within its warehouse. */
    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 20) private String aisle;
    @Column(length = 20) private String rack;
    @Column(length = 20) private String bin;

    /**
     * False for staging, inspection and damage locations: they hold stock but must never be
     * picked from. Picking filters on this rather than on a naming convention.
     */
    @Column(nullable = false)
    private boolean pickable = true;

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
