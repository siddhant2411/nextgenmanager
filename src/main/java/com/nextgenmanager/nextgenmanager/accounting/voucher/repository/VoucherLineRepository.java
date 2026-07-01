package com.nextgenmanager.nextgenmanager.accounting.voucher.repository;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherLine;
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
        WHERE v.status = 'POSTED'
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date <= :asOf
        GROUP BY vl.ledgerAccount.id
        """)
    List<Object[]> aggregateByAccount(@Param("asOf") LocalDate asOf);

    /**
     * Ledger statement: all posted lines for one account in date range,
     * ordered chronologically. Returns Object[]{date, voucherNumber, voucherType, narration, drAmount, crAmount}.
     */
    @Query("""
        SELECT v.date, v.voucherNumber, v.voucherType, vl.narration, vl.drAmount, vl.crAmount
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE vl.ledgerAccount.id = :accountId
          AND v.status = 'POSTED'
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
        SELECT vl.ledgerAccount.id, v.date, vl.drAmount, vl.crAmount
        FROM VoucherLine vl
        JOIN vl.voucher v
        WHERE vl.ledgerAccount.subLedgerType = :type
          AND vl.ledgerAccount.deletedDate IS NULL
          AND v.status = 'POSTED'
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
        WHERE v.status = 'POSTED'
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
        WHERE v.status = 'POSTED'
          AND v.deletedDate IS NULL
          AND vl.deletedDate IS NULL
          AND v.date <= :asOf
          AND vl.ledgerAccount.code IN :codes
        GROUP BY vl.ledgerAccount.code
        """)
    List<Object[]> balanceByCodeAsOf(@Param("asOf") LocalDate asOf, @Param("codes") List<String> codes);
}
