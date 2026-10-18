package com.nextgenmanager.nextgenmanager.accounting.voucher.repository;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherLine;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VoucherLineRepository extends JpaRepository<VoucherLine, Long> {

    /** True if any non-deleted voucher line references this ledger account (blocks deletion). */
    boolean existsByLedgerAccount_IdAndDeletedDateIsNull(Long ledgerAccountId);

    /**
     * Aggregates for Trial Balance: sum Dr and Cr per ledger account for all
     * POSTED vouchers dated up to asOf. Returns Object[]{accountId, sumDr, sumCr}.
     */
    @Query("""
        SELECT vl.ledgerAccount.id, COALESCE(SUM(vl.drAmount), 0), COALESCE(SUM(vl.crAmount), 0)
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE v.status IN ('POSTED', 'REVERSED')
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date <= :asOf
        GROUP BY vl.ledgerAccount.id
        """)
    List<Object[]> aggregateByAccount(@Param("asOf") LocalDate asOf);

    /**
     * Dr/Cr movement per ledger account for POSTED vouchers dated within a range, leaving out one
     * voucher type. The P&L passes CLOSING so a closed year still shows its income and expenses
     * (the closing voucher only moves them to Retained Earnings). Returns Object[]{accountId, sumDr, sumCr}.
     */
    @Query("""
        SELECT vl.ledgerAccount.id, COALESCE(SUM(vl.drAmount), 0), COALESCE(SUM(vl.crAmount), 0)
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE v.status IN ('POSTED', 'REVERSED')
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date BETWEEN :from AND :to
          AND v.voucherType <> :excludeType
        GROUP BY vl.ledgerAccount.id
        """)
    List<Object[]> aggregateByAccountInRange(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("excludeType") VoucherType excludeType);

    /**
     * Ledger statement: all posted lines for one account in date range,
     * ordered chronologically. Returns Object[]{date, voucherNumber, voucherType, narration, drAmount, crAmount,
     * voucherId, sourceDocType, sourceDocId} — the last three let the statement open the voucher and its document.
     * The narration is the line's own, else the voucher's: most lines carry none, which left the statement blank.
     */
    @Query("""
        SELECT v.date, v.voucherNumber, v.voucherType, COALESCE(vl.narration, v.narration), vl.drAmount, vl.crAmount,
               v.id, v.sourceDocType, v.sourceDocId
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE vl.ledgerAccount.id = :accountId
          AND v.status IN ('POSTED', 'REVERSED')
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date BETWEEN :from AND :to
        ORDER BY v.date ASC, v.voucherNumber ASC
        """)
    List<Object[]> ledgerStatement(@Param("accountId") Long accountId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /**
     * All posted lines for every party sub-ledger of a given type (CUSTOMER/VENDOR) up to asOf,
     * ordered by account then chronologically — drives FIFO open-item ageing.
     * Returns Object[]{ledgerAccountId, date, drAmount, crAmount}.
     */
    @Query("""
        SELECT vl.ledgerAccount.id, v.date, vl.drAmount, vl.crAmount, v.voucherType
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE vl.ledgerAccount.subLedgerType = :type
          AND vl.ledgerAccount.deletedDate IS NULL
          AND v.status IN ('POSTED', 'REVERSED')
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date <= :asOf
        ORDER BY vl.ledgerAccount.id ASC, v.date ASC, v.voucherNumber ASC
        """)
    List<Object[]> partyLedgerLines(@Param("type") SubLedgerType type, @Param("asOf") LocalDate asOf);

    /**
     * GST reconciliation: Dr/Cr movement of the given ledger codes within a date range, for POSTED
     * vouchers. Returns Object[]{ledgerCode, sumDr, sumCr}. Output-GST heads are net-credit
     * (Cr−Dr); Input-GST heads are net-debit (Dr−Cr).
     */
    @Query("""
        SELECT vl.ledgerAccount.code, COALESCE(SUM(vl.drAmount), 0), COALESCE(SUM(vl.crAmount), 0)
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE v.status IN ('POSTED', 'REVERSED')
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date BETWEEN :from AND :to
          AND vl.ledgerAccount.code IN :codes
        GROUP BY vl.ledgerAccount.code
        """)
    List<Object[]> movementByCodeInRange(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("codes") List<String> codes);

    /**
     * Net Dr/Cr balance of the given ledger codes for all POSTED vouchers up to asOf.
     * Returns Object[]{ledgerCode, sumDr, sumCr}. Drives the perpetual-inventory GL↔stock reconciliation.
     */
    @Query("""
        SELECT vl.ledgerAccount.code, COALESCE(SUM(vl.drAmount), 0), COALESCE(SUM(vl.crAmount), 0)
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE v.status IN ('POSTED', 'REVERSED')
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date <= :asOf
          AND vl.ledgerAccount.code IN :codes
        GROUP BY vl.ledgerAccount.code
        """)
    List<Object[]> balanceByCodeAsOf(@Param("asOf") LocalDate asOf, @Param("codes") List<String> codes);

    /**
     * Bank reconciliation's book side: every line on one ledger up to asOf, from vouchers that still
     * stand. A reversed voucher and its reversal net to nothing, so neither is offered for matching.
     */
    @Query("""
        SELECT vl FROM VoucherLine vl
        JOIN FETCH vl.voucher v
        WHERE vl.ledgerAccount.id = :ledgerId
          AND vl.deletedDate IS NULL
          AND v.deletedDate IS NULL
          AND v.status = 'POSTED'
          AND v.reversalOf IS NULL
          AND v.date <= :asOf
        ORDER BY v.date ASC, v.voucherNumber ASC
        """)
    List<VoucherLine> standingLinesUpTo(@Param("ledgerId") Long ledgerId, @Param("asOf") LocalDate asOf);
}
