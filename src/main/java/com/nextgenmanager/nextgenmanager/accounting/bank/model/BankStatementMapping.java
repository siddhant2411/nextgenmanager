package com.nextgenmanager.nextgenmanager.accounting.bank.model;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.Date;

/**
 * How one bank account's statement export is laid out. Columns are named by their header text, so
 * the same mapping keeps working when a bank adds or reorders columns.
 */
@Entity
@Table(name = "bankstatementmapping")
@Getter @Setter @NoArgsConstructor
public class BankStatementMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledgerAccount_id", nullable = false, unique = true)
    private LedgerAccount ledgerAccount;

    /** 1-based row holding the column titles — banks often put account details above the table. */
    private int headerRow = 1;

    @Column(nullable = false, length = 120)
    private String dateColumn;
    @Column(length = 40)
    private String dateFormat;
    @Column(length = 120)
    private String narrationColumn;
    @Column(length = 120)
    private String referenceColumn;
    @Column(length = 120)
    private String withdrawalColumn;
    @Column(length = 120)
    private String depositColumn;
    /** One signed column (deposits positive), used when withdrawal and deposit columns are both blank. */
    @Column(length = 120)
    private String amountColumn;
    @Column(length = 120)
    private String balanceColumn;

    /** Book entries dated before this count as already cleared (the opening balance at a mid-year go-live). */
    private LocalDate reconcileFrom;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    @UpdateTimestamp
    private Date updatedDate;
}
