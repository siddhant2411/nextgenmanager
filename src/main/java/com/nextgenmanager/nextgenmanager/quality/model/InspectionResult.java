package com.nextgenmanager.nextgenmanager.quality.model;

import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One thing checked on a lot, and what was found.
 *
 * <p>Only lots whose source has nowhere else to keep measurements carry these — incoming goods,
 * finished goods and packages. An in-process lot has none: the operator's readings already live on
 * the operation as {@code WorkOrderQaResult}, and the lot rolls those up rather than asking for
 * them twice.
 */
@Entity
@Table(name = "inspectionresult")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class InspectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspectionlot_id", nullable = false)
    private InspectionLot inspectionLot;

    @Column(nullable = false, length = 200)
    private String parameterName;

    @Column(length = 30)
    private String parameterType;

    @Column(precision = 18, scale = 4) private BigDecimal minValue;
    @Column(precision = 18, scale = 4) private BigDecimal maxValue;
    @Column(length = 30) private String unit;

    /**
     * A failure here fails the whole lot. Non-critical failures are recorded and reported, but a
     * scratch on a casting is not a reason to stop a shipment on its own.
     */
    @Column(nullable = false)
    private boolean critical = false;

    @Column(precision = 18, scale = 4) private BigDecimal observedValue;

    /** For visual and other judgement checks, where there is no number to record. */
    @Column(length = 300) private String observedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QaResult result = QaResult.PENDING;

    @Column(length = 300) private String remarks;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;

    /**
     * Whether the observed value sits inside the specification, for the numeric checks where that
     * can be decided without a person. A check with no limits, or nothing observed yet, is not
     * something this can answer — it returns empty and the inspector says.
     */
    public Boolean withinSpecification() {
        if (observedValue == null || (minValue == null && maxValue == null)) return null;
        if (minValue != null && observedValue.compareTo(minValue) < 0) return false;
        if (maxValue != null && observedValue.compareTo(maxValue) > 0) return false;
        return true;
    }
}
