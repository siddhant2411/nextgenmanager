package com.nextgenmanager.nextgenmanager.accounting.voucher.model;

import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "voucher")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private VoucherType voucherType;

    @Column(unique = true, nullable = false, length = 40)
    private String voucherNumber;

    @Column(nullable = false)
    private java.time.LocalDate date;

    @Column(length = 500)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 18)
    private VoucherStatus status = VoucherStatus.DRAFT;

    /** Links back to originating document — idempotency key with sourceDocId. */
    @Column(length = 40)
    private String sourceDocType;

    private Long sourceDocId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financialYear_id", nullable = false)
    private FinancialYear financialYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private AccountingPeriod period;

    /** The voucher this is a reversal of. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversalOf_id")
    private Voucher reversalOf;

    /** The reversal voucher that reversed this one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversedByVoucher_id")
    private Voucher reversedByVoucher;

    @Column(length = 100)
    private String createdBy;

    // Lines: LAZY by JPA default. Use JOIN FETCH in detail queries.
    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<VoucherLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
