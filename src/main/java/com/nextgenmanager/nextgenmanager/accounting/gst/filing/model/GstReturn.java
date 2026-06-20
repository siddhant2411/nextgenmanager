package com.nextgenmanager.nextgenmanager.accounting.gst.filing.model;

import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Immutable snapshot of a filed GST return: the totals plus the full JSON payload at filing time.
 * Superseded filings are soft-deleted (a fresh re-file after unlock creates a new row).
 */
@Entity
@Table(name = "gstreturn")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GstReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financialYear_id", nullable = false)
    private FinancialYear financialYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private AccountingPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GstReturnType returnType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GstReturnStatus status = GstReturnStatus.FILED;

    @Column(length = 40)
    private String periodLabel;

    @Column(precision = 14, scale = 2, nullable = false) private BigDecimal taxableValue = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2, nullable = false) private BigDecimal cgst = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2, nullable = false) private BigDecimal sgst = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2, nullable = false) private BigDecimal igst = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2, nullable = false) private BigDecimal cess = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2, nullable = false) private BigDecimal total = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(length = 100)
    private String filedBy;

    private Date filedDate;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
