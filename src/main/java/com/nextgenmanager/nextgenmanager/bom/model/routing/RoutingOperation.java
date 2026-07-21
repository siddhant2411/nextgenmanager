package com.nextgenmanager.nextgenmanager.bom.model.routing;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.LaborRole;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.ProductionJob;
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

    /**
     * RATE_TIMES_QTY only: how many eaches (holes / kg / tests) this BOM consumes of the operation.
     * The per-each rate comes from {@link #costRate} if set, else the linked
     * {@code ProductionJob.defaultPieceRate}. Operation cost = rate × costQuantity.
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal costQuantity;

    /**
     * RATE_TIMES_QTY only: optional per-operation override of the piece-rate. Leave null to inherit
     * the shared {@code ProductionJob.defaultPieceRate} (recommended, so a rate change flows to all
     * BOMs). Set only when this operation genuinely deviates from the standard rate.
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal costRate;

    /**
     * RATE_TIMES_QTY only: when true, the work center's overhead% is loaded on top of the
     * piece-rate subtotal (rate × costQuantity). Defaults to false — a piece rate is treated as an
     * all-in price unless this is explicitly enabled. Ignored for other cost types (CALCULATED
     * always applies overhead; FIXED_RATE / SUB_CONTRACTED never do).
     */
    @Column(nullable = false)
    private Boolean applyOverhead = false;

    private BigDecimal setupTime;
    private BigDecimal runTime;
    private Boolean inspection;
    private String notes;
    @Column(columnDefinition = "TEXT")
    private String instructions;

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
