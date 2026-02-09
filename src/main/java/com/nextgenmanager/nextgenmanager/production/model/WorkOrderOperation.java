package com.nextgenmanager.nextgenmanager.production.model;


import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "WorkOrderOperation")
public class WorkOrderOperation {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ---- Parent Work Order ----
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routingOperationId")
    private RoutingOperation routingOperation;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private String operationName;           // Cutting, Welding

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId")
    private WorkCenter workCenter;


    // ---- Quantities ----
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal plannedQuantity;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal completedQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    private Date plannedStartDate;
    private Date plannedEndDate;

    private Date actualStartDate;
    private Date actualEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    private Boolean isMilestone = false;
    private Boolean allowOverCompletion = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;



}
