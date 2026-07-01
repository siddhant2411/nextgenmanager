package com.nextgenmanager.nextgenmanager.accounting.tds.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * One TDS deduction, deductee-wise — the source for the 26Q quarterly return. Created by the
 * accounting side when a vendor payment carries a TDS amount; idempotent on
 * {@code (sourceDocType, sourceDocId)}. Flips to {@code DEPOSITED} when included in a challan (V144).
 */
@Entity
@Table(name = "tdsentry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TdsEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private TdsSection section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    /** Originating document — currently always {@code VENDOR_PAYMENT}. */
    @Column(nullable = false, length = 40)
    private String sourceDocType;

    @Column(nullable = false)
    private Long sourceDocId;

    /** Amount on which TDS was computed (the gross amount paid). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal tdsAmount;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal rate;

    /** Indian financial year string, e.g. "2025-26". */
    @Column(nullable = false, length = 9)
    private String financialYear;

    /** "Q1".."Q4" (Q1 = Apr-Jun). */
    @Column(nullable = false, length = 2)
    private String quarter;

    @Column(nullable = false)
    private LocalDate deductionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TdsEntryStatus status = TdsEntryStatus.DEDUCTED;

    /** Set when deposited via a TdsChallan (V144). Loose id link. */
    private Long challanId;

    private Date deletedDate;
}
