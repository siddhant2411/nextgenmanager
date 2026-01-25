package com.nextgenmanager.nextgenmanager.production.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderProductionTemplate")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WorkOrderProductionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id", referencedColumnName = "id")
    private Bom bom;

    @OneToMany(mappedBy = "workOrderProductionTemplate",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<WorkOrderJobList> workOrderJobLists;

    // Cost & Time fields (optional for MVP)
    private BigDecimal totalSetupTime;
    private BigDecimal totalRunTime;
    private BigDecimal estimatedHours;

    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedCostOfLabour;

    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedCostOfBom;

    @Column(precision = 10, scale = 2)
    private BigDecimal overheadCostPercentage;

    @Column(precision = 10, scale = 2)
    private BigDecimal overheadCostValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCostOfWorkOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_work_center_id")
    private WorkCenter defaultWorkCenter;

    private String details;

    @OneToMany(mappedBy = "workOrderProductionTemplate",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<WorkOrderProductionTemplateDocument> workOrderProductionTemplateDocuments;

    @Enumerated(EnumType.ORDINAL)
    private CostingMethod costingMethod = CostingMethod.AUTO;

    public enum CostingMethod {
        AUTO,
        MANUAL_OVERRIDE
    }

    @Column(nullable = false)
    private Integer versionNumber;        // increment manually per update

    @Column(nullable = false)
    private Boolean isActiveVersion;      // only 1 active version per BOM

    @Column(name = "effective_from")
    private Date effectiveFrom;

    @Column(name = "effective_to")
    private Date effectiveTo;

    private String changeReason;
    private String changedBy;


    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
