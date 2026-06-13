package com.nextgenmanager.nextgenmanager.accounting.voucher.repository;

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
}
