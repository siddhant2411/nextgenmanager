package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "rejectionEntry")
public class RejectionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operationId", nullable = false)
    private WorkOrderOperation operation;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal rejectedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispositionStatus dispositionStatus = DispositionStatus.PENDING;

    @Column(length = 500)
    private String dispositionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "childWorkOrderId")
    private WorkOrder childWorkOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Date createdAt;

    private Date disposedAt;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String disposedBy;
}
