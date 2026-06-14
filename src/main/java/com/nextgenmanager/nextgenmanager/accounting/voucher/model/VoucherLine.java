package com.nextgenmanager.nextgenmanager.accounting.voucher.model;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "voucherline")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VoucherLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    /** 1-based position as entered — preserves display order for printing/audit. */
    @Column(nullable = false)
    private Integer lineNumber = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledgerAccount_id", nullable = false)
    private LedgerAccount ledgerAccount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal drAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal crAmount = BigDecimal.ZERO;

    @Column(length = 300)
    private String narration;

    /** Reserved for Phase 4 cost-center tracking. */
    private Long costCenter_id;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private TaxLineType taxType;

    @Column(length = 10)
    private String hsnSac;

    @Column(precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate;

    private Date deletedDate;
}
