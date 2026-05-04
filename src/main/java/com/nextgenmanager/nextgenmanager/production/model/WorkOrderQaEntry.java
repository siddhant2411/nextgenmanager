package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.bom.enums.QaParameterType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "WorkOrderQaEntry")
public class WorkOrderQaEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderOperationId", nullable = false)
    private WorkOrderOperation workOrderOperation;

    private Long bomQaParameterId;

    @Column(nullable = false)
    private String parameterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QaParameterType parameterType;

    @Column(precision = 15, scale = 4)
    private BigDecimal minValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal maxValue;

    private String unit;

    @Column(nullable = false)
    private Boolean critical = false;

    @OneToMany(mappedBy = "workOrderQaEntry", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("checkedAt DESC")
    private List<WorkOrderQaResult> results = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;
}
