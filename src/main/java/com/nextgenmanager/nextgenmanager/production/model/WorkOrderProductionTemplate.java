package com.nextgenmanager.nextgenmanager.production.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
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
public class WorkOrderProductionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @OneToOne(fetch = FetchType.LAZY) // Lazy load the associated bom
    @JoinColumn(name = "bom_id", referencedColumnName = "id") // Foreign key mapping
    private Bom bom;

    @OneToMany(mappedBy = "workOrderProductionTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<WorkOrderJobList> workOrderJobLists;

    @Column(precision = 10, scale = 1)
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

    private String details;


    @OneToMany(mappedBy = "workOrderProductionTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<WorkOrderProductionTemplateDocument> workOrderProductionTemplateDocuments;


    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

}
