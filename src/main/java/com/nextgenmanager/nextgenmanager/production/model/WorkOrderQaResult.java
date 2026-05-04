package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "WorkOrderQaResult")
public class WorkOrderQaResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderQaEntryId", nullable = false)
    private WorkOrderQaEntry workOrderQaEntry;

    @Column(precision = 15, scale = 5)
    private BigDecimal checkedQuantity;

    @Column(precision = 15, scale = 4)
    private BigDecimal actualValue;

    private Boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QaResult result = QaResult.PENDING;

    @Column(length = 500)
    private String remarks;

    @Column(length = 150)
    private String checkedBy;

    private Date checkedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
}
