package com.nextgenmanager.nextgenmanager.accounting.tds.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * A TDS section master row (e.g. 194C, 194J): the statutory rate, the higher PAN-missing rate,
 * and advisory deduction thresholds. Referenced by {@link TdsEntry} and looked up by code from
 * the vendor-payment flow.
 */
@Entity
@Table(name = "tdssection")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TdsSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Section code, e.g. "194C", "194J", "194Q". Unique. */
    @Column(nullable = false, unique = true, length = 20)
    private String section;

    @Column(nullable = false, length = 200)
    private String description;

    /** Standard rate in percent (e.g. 2 for 2%, 0.1 for 194Q). */
    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal rate;

    /** Higher rate applied when the deductee has no PAN (typically 20%). */
    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal panMissingRate = new BigDecimal("20");

    /** Per-transaction threshold below which TDS need not be deducted (advisory). */
    @Column(precision = 14, scale = 2)
    private BigDecimal thresholdSingle;

    /** Cumulative annual threshold (advisory). */
    @Column(precision = 14, scale = 2)
    private BigDecimal thresholdAnnual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TdsApplicableTo applicableTo = TdsApplicableTo.VENDOR_PAYMENT;

    @Column(nullable = false)
    private boolean active = true;

    private Date deletedDate;
}
