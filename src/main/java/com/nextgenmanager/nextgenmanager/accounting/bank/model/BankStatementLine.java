package com.nextgenmanager.nextgenmanager.accounting.bank.model;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherLine;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * One row of an imported bank statement. Sides are the bank's: a deposit is money in (a debit to the
 * bank ledger in the books), a withdrawal is money out (a credit). Matched 1:1 to the voucher line
 * that books it.
 */
@Entity
@Table(name = "bankstatementline")
@Getter @Setter @NoArgsConstructor
public class BankStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledgerAccount_id", nullable = false)
    private LedgerAccount ledgerAccount;

    @Column(nullable = false)
    private LocalDate txnDate;
    @Column(length = 500)
    private String narration;
    @Column(length = 120)
    private String reference;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal withdrawal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal deposit = BigDecimal.ZERO;
    @Column(precision = 16, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 40)
    private String importBatch;
    @Column(length = 255)
    private String sourceFileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucherLine_id")
    private VoucherLine voucherLine;
    @Column(length = 100)
    private String matchedBy;
    private Date matchedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    private Date deletedDate;

    /** Deposit positive, withdrawal negative — the same sign as Dr − Cr on the bank ledger. */
    public BigDecimal signedAmount() {
        return deposit.subtract(withdrawal);
    }
}
