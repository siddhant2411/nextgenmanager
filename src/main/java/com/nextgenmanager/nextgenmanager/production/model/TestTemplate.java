package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.enums.InspectionType;
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
 * Defines a reusable quality-control test for a finished product.
 * Anchored to InventoryItem — same product gets the same tests
 * regardless of which BOM revision is used.
 *
 * When a WorkOrder is created, active templates are copied into
 * WorkOrderTestResult rows (frozen snapshot).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "testTemplate",
        indexes = {
                @Index(name = "idx_tt_item", columnList = "inventoryItemId")
        })
public class TestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    @Column(nullable = false, length = 150)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InspectionType inspectionType;

    /** Number of units to sample. null = 100% inspection */
    private Integer sampleSize;

    @Column(nullable = false)
    private Boolean isMandatory = true;

    private Integer sequence;

    /** Human-readable acceptance criteria, e.g. "Ra ≤ 1.6 µm" */
    @Column(length = 500)
    private String acceptanceCriteria;

    @Column(length = 20)
    private String unitOfMeasure;

    /** Lower specification limit */
    @Column(precision = 15, scale = 5)
    private BigDecimal minValue;

    /** Upper specification limit */
    @Column(precision = 15, scale = 5)
    private BigDecimal maxValue;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
