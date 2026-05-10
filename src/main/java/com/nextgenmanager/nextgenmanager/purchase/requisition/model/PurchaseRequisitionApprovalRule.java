package com.nextgenmanager.nextgenmanager.purchase.requisition.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchaseRequisitionApprovalRule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal minAmount;

    /** NULL means no upper bound — applies to all amounts >= minAmount. */
    @Column(precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false, length = 50)
    private String requiredRole;

    @Column(nullable = false)
    private boolean active = true;
}
