package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.production.enums.InspectionType;
import com.nextgenmanager.nextgenmanager.production.enums.TestResult;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * A frozen copy of a TestTemplate for a specific Work Order.
 * Operators fill in actual test results here.
 * Used to generate the final Testing / QC report.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderTestResult",
        indexes = {
                @Index(name = "idx_wotr_wo", columnList = "workOrderId"),
                @Index(name = "idx_wotr_template", columnList = "testTemplateId")
        })
public class WorkOrderTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workOrderId", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "testTemplateId")
    private TestTemplate testTemplate;

    // ---- Snapshot from template (frozen at WO creation) ----

    @Column(length = 150)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private InspectionType inspectionType;

    private Integer sampleSize;

    private Boolean isMandatory;

    private Integer sequence;

    @Column(length = 500)
    private String acceptanceCriteria;

    @Column(length = 20)
    private String unitOfMeasure;

    @Column(precision = 15, scale = 5)
    private BigDecimal minValue;

    @Column(precision = 15, scale = 5)
    private BigDecimal maxValue;

    // ---- Actual results (filled by operator) ----

    /** Measured value for single-value tests */
    @Column(precision = 15, scale = 5)
    private BigDecimal resultValue;

    /** How many units were tested */
    private Integer testedQuantity;

    /** How many passed */
    private Integer passedQuantity;

    /** How many failed */
    private Integer failedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TestResult result = TestResult.PENDING;

    @Column(length = 100)
    private String testedBy;

    private Date testDate;

    @Column(length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;
}
