package com.nextgenmanager.nextgenmanager.quality.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * What was decided about goods that failed inspection.
 *
 * <p>The lot says they failed; this says what happened next. Kept as its own record because the
 * two questions are answered by different people at different times — an inspector finds a
 * problem in the morning, and somebody decides that afternoon whether it is reworked, scrapped,
 * shipped anyway or sent back.
 */
@Entity
@Table(name = "nonconformancereport")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NonConformanceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String ncrNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspectionlot_id", nullable = false)
    private InspectionLot inspectionLot;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    /** What is actually wrong, in words. A report that cannot say is not worth raising. */
    @Column(nullable = false, length = 1000)
    private String problem;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NcrDisposition disposition;

    @Column(length = 100) private String dispositionedBy;
    @Temporal(TemporalType.TIMESTAMP) private Date dispositionedDate;
    @Column(length = 1000) private String dispositionNotes;

    /** Who agreed to use goods as they are. Required for USE_AS_IS and meaningless otherwise. */
    @Column(length = 100) private String approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NcrStatus status = NcrStatus.OPEN;

    @Column(length = 100) private String raisedBy;
    @Column(length = 500) private String remarks;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date deletedDate;
}
