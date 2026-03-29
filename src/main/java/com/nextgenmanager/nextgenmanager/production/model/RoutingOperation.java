package com.nextgenmanager.nextgenmanager.production.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "routingOperation")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RoutingOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routingId", nullable = false)
    private Routing routing;

    private Integer sequenceNumber;
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productionJobId")
    private ProductionJob productionJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId")
    private WorkCenter workCenter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "laborRoleId")
    private LaborRole laborRole;

    private Integer numberOfOperators = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machineDetailsId")
    private MachineDetails machineDetails;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CostType costType = CostType.CALCULATED;

    @Column(precision = 10, scale = 2)
    private BigDecimal fixedCostPerUnit;

    private BigDecimal setupTime;
    private BigDecimal runTime;
    private Boolean inspection;
    private String notes;

    // ---- Parallel Operation Fields ----

    /**
     * When true, this operation can run concurrently with other operations
     * that have no sequential dependency on it.
     */
    @Column(nullable = false)
    private Boolean allowParallel = false;

    /**
     * Optional label to group operations that belong to the same parallel path.
     * E.g. "PATH_A", "PATH_B". Operations with the same parallelPath are part
     * of the same concurrent execution stream.
     */
    @Column(length = 50)
    private String parallelPath;

    /**
     * Explicit dependency declarations for this operation.
     * If empty, falls back to sequence-number-based ordering (legacy behaviour).
     * If populated, only these declared dependencies are enforced.
     */
    @OneToMany(mappedBy = "routingOperation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoutingOperationDependency> dependencies = new ArrayList<>();
}
