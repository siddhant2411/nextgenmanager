package com.nextgenmanager.nextgenmanager.production.model;

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
@Table(name = "WorkOrderMaterialReorder")
public class WorkOrderMaterialReorder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderMaterialId", nullable = false)
    private WorkOrderMaterial workOrderMaterial;

    private Long inventoryRequestId;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal requestedQuantity;

    @Column(nullable = false, precision = 15, scale = 5)
    private BigDecimal shortfallQuantity = BigDecimal.ZERO;

    private String remarks;

    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;
}
