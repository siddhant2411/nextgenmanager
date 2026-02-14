package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.enums.MaterialIssueStatus;
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
@Table(name = "WorkOrderMaterial")
public class WorkOrderMaterial {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Parent Work Order ----
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "componentId", nullable = false)
    private InventoryItem component;

    // ---- Quantities ----

    // 100 (real need)
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal netRequiredQuantity;

    // 110 (100 + 10% scrap buffer)
    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal plannedRequiredQuantity;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal scrappercent = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal issuedQuantity = BigDecimal.ZERO;

    @Column(precision = 15, scale = 5)
    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialIssueStatus issueStatus;

    /***
     * This gives you future flexibility:
     * false → manual material issue
     * true → auto-issue at operation completion
     */
    private Boolean backflush = false;



    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;


}
