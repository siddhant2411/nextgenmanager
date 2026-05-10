package com.nextgenmanager.nextgenmanager.purchase.requisition.model;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "purchaseRequisition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String prNumber;

    private Date requestDate;

    private Date requiredByDate;

    @Column(length = 100)
    private String requestedBy;

    @Column(length = 100)
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId")
    private WorkCenter workCenter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequisitionPriority priority = PurchaseRequisitionPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequisitionStatus status = PurchaseRequisitionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequisitionApprovalStatus approvalStatus = PurchaseRequisitionApprovalStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequisitionSource source = PurchaseRequisitionSource.MANUAL;

    /** Optional reference to the source entity (e.g., workOrderId, salesOrderId). */
    private Long sourceRefId;

    @Column(length = 100)
    private String sourceRefNumber;

    /** Sum of line estimatedAmount — used for approval rule routing. */
    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal totalEstimatedAmount = BigDecimal.ZERO;

    private String approvedBy;
    private Date approvedDate;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<PurchaseRequisitionItem> items;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
