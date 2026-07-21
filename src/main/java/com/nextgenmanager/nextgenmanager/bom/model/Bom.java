package com.nextgenmanager.nextgenmanager.bom.model;

import com.fasterxml.jackson.annotation.*;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bom")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "bomName")
    private String bomName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentInventoryItemId", nullable = false)
    private InventoryItem parentInventoryItem;

    @OneToMany(mappedBy = "parentBom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomPosition> positions;

    @OneToMany(mappedBy = "parentBom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomCostLine> costLines;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private BomStatus bomStatus;

    @Column(name = "effectiveFrom")
    private Date effectiveFrom;

    @Column(name = "effectiveTo")
    private Date effectiveTo;

    @Column(name = "version")
    private String revision;

    @Column(name = "ecoNumber")
    private String ecoNumber;

    @Column(name = "changeReason")
    private String changeReason;

    @Column(name = "approvedBy")
    private String approvedBy;

    @Column(name = "approvalDate")
    private Date approvalDate;

    @Column(name = "approvalComments")
    private String approvalComments;

    @Column(name = "description")
    private String description;

    /**
     * Blanket manufacturing overhead %, applied at the cost rollup to the total manufacturing base
     * (material + in-house conversion + consumables + fixed-rate operations), excluding
     * subcontracted operations. Replaces the legacy per-work-center overhead. Null = 0%.
     */
    @Column(name = "overheadPercentage", precision = 6, scale = 3)
    private java.math.BigDecimal overheadPercentage;

    @Column(name = "isActive")
    private Boolean isActive = false;

    @Column(name = "isDefault")
    private Boolean isDefault;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private Boolean isActiveVersion = false;

    @Column(nullable = false)
    private String versionGroup;


    @OneToOne(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    private Routing routing;


    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

}
