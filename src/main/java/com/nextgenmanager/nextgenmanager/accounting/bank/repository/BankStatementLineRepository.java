package com.nextgenmanager.nextgenmanager.accounting.bank.repository;

import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Long> {

    Optional<BankStatementLine> findByIdAndDeletedDateIsNull(Long id);

    /** Every live statement line of the account up to a date, oldest first, with its matched book line. */
    @Query("""
        SELECT s FROM BankStatementLine s
        LEFT JOIN FETCH s.voucherLine vl
        LEFT JOIN FETCH vl.voucher
        WHERE s.ledgerAccount.id = :ledgerId
          AND s.deletedDate IS NULL
          AND s.txnDate <= :asOf
        ORDER BY s.txnDate ASC, s.id ASC
        """)
    List<BankStatementLine> findLiveUpTo(@Param("ledgerId") Long ledgerId, @Param("asOf") LocalDate asOf);

    @Query("""
        SELECT s FROM BankStatementLine s
        LEFT JOIN FETCH s.voucherLine vl
        LEFT JOIN FETCH vl.voucher
        WHERE s.ledgerAccount.id = :ledgerId
          AND s.deletedDate IS NULL
        ORDER BY s.txnDate ASC, s.id ASC
        """)
    List<BankStatementLine> findLive(@Param("ledgerId") Long ledgerId);

    /** Voucher-line ids already cleared against a live statement line of this account. */
    @Query("""
        SELECT s.voucherLine.id FROM BankStatementLine s
        WHERE s.ledgerAccount.id = :ledgerId AND s.deletedDate IS NULL AND s.voucherLine IS NOT NULL
        """)
    List<Long> matchedVoucherLineIds(@Param("ledgerId") Long ledgerId);

    boolean existsByVoucherLineIdAndDeletedDateIsNull(Long voucherLineId);
}
