package com.nextgenmanager.nextgenmanager.accounting.tds.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * A TDS deposit challan (ITNS 281). Aggregates the {@link TdsEntry} rows deposited for a
 * financial year + quarter (optionally one section), flips them to DEPOSITED, and triggers
 * the GL posting {@code Dr TDS Payable / Cr Bank}.
 */
@Entity
@Table(name = "tdschallan")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TdsChallan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Challan serial / CIN. */
    @Column(nullable = false, length = 50)
    private String challanNumber;

    @Column(length = 20)
    private String bsrCode;

    @Column(nullable = false)
    private LocalDate depositDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Section code if the challan is section-specific; null when it covers all sections. */
    @Column(length = 20)
    private String section;

    @Column(nullable = false, length = 9)
    private String financialYear;

    @Column(nullable = false, length = 2)
    private String quarter;

    @Column(length = 500)
    private String notes;

    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
