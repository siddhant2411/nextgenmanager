package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

/**
 * One engineering revision of an {@link InventoryItem} — the "A", "B", "C" that a drawing carries.
 *
 * The item row itself never changes shape: name, item code, financial settings and inventory
 * policy stay on {@link InventoryItem} and are editable regardless of lock state. Everything that
 * describes what the part physically IS lives here instead, so that a released revision can be
 * frozen without freezing the price, and a BOM position can pin exactly which drawing it was built
 * against instead of "whatever the item currently says".
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "itemRevision")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ItemRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Serializing this back out would walk InventoryItem -> currentRevision -> inventoryItem ->
    // currentRevision -> ... forever. Callers already have the item; they never need it from here.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    /** "A", "B", "C"... — see {@code com.nextgenmanager.nextgenmanager.bom.service.BomServiceImpl#toRevision}. */
    @Column(nullable = false, length = 10)
    private String revisionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemRevisionStatus status;

    /**
     * Whether this revision is form-fit-function interchangeable with the one it supersedes. Null
     * until answered (asked when the revision is released). true = existing stock of the prior
     * revision may still be consumed, open POs need no amendment, BOMs may roll forward in bulk.
     * false = a where-used impact list must be worked through by hand.
     */
    private Boolean interchangeable;

    private String ecoNumber;

    @Column(length = 1000)
    private String changeReason;

    private String releasedBy;

    private Date releasedOn;

    /** Set when a later revision supersedes this one. Points forward, never edited after the fact. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersededById")
    private ItemRevision supersededBy;

    private Date effectiveFrom;

    private Date effectiveTo;

    /* ── Engineering attributes (what ProductSpecification used to hold) ── */
    private String dimension;
    private String size;
    private String weight;
    private String basicMaterial;
    private String processType;
    private String drawingNumber;

    private UOM uom;

    private String hsnCode;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    public boolean isLocked() {
        return status == ItemRevisionStatus.RELEASED
                || status == ItemRevisionStatus.SUPERSEDED
                || status == ItemRevisionStatus.OBSOLETE
                || status == ItemRevisionStatus.PENDING_APPROVAL;
    }
}
