package com.nextgenmanager.nextgenmanager.production.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderJobList")
public class WorkOrderJobList {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    // Sequence of the operation (10,20,30...)
    @Column(nullable = false)
    private Integer operationNumber;

    // Name or reference to a master ProductionJob
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_job_id", nullable = false)
    private ProductionJob productionJob;

    // Work center or department where operation is performed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId", referencedColumnName = "id")
    private WorkCenter workCenter;

    // Setup time (fixed time per batch)
    @Column(precision = 10, scale = 2)
    private BigDecimal setupTime;

    // Runtime per unit (time required to produce 1 unit)
    @Column(precision = 10, scale = 2)
    private BigDecimal runTimePerUnit;

    // Cost values (auto or manually calculated)
    @Column(precision = 10, scale = 2)
    private BigDecimal labourCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal overheadCost;

    // Operator instructions or process notes
    @Column(length = 1000)
    private String operationDescription;

    // Whether this operation can run parallel with others
    private Boolean isParallelOperation = false;

    // Link to the routing/production template
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wopt_id", nullable = false)
    @JsonBackReference
    private WorkOrderProductionTemplate workOrderProductionTemplate;

    // Optional: Tools required (left flexible)
    @Column(length = 500)
    private String toolingRequirements;

    // Optional: Skill level needed for operation (1-5)
    private Integer skillLevelRequired;
}