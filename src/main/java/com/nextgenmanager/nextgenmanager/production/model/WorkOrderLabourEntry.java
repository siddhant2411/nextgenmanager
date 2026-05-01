package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.production.enums.LabourType;
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
@Table(name = "WorkOrderLabourEntry")
public class WorkOrderLabourEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderOperationId", nullable = false)
    private WorkOrderOperation workOrderOperation;

    @Column(length = 100)
    private String operatorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "laborRoleId")
    private LaborRole laborRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LabourType laborType = LabourType.RUN;

    private Date startTime;

    private Date endTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal durationMinutes;

    @Column(precision = 10, scale = 2)
    private BigDecimal costRatePerHour;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
