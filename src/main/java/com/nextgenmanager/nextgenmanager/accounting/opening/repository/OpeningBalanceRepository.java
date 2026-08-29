package com.nextgenmanager.nextgenmanager.accounting.opening.repository;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.opening.model.OpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OpeningBalanceRepository extends JpaRepository<OpeningBalance, Long> {

    List<OpeningBalance> findByOpeningDateAndDeletedDateIsNull(LocalDate openingDate);

    boolean existsByOpeningDateAndDeletedDateIsNull(LocalDate openingDate);

    /**
     * Dated open bills carried over at cutover, for ageing (V162).
     *
     * <p>Only rows that actually carry a {@code billDate} — a lump ledger balance has none and is
     * aged at the opening voucher's own date, exactly as before. Bounded by {@code openingDate} so
     * a report run before the cutover does not pick up bills from it.
     */
    @Query("""
        SELECT ob FROM OpeningBalance ob
        WHERE ob.ledgerAccount.subLedgerType = :type
          AND ob.billDate IS NOT NULL
          AND ob.openingDate <= :asOf
          AND ob.deletedDate IS NULL
        ORDER BY ob.ledgerAccount.id ASC, ob.billDate ASC
        """)
    List<OpeningBalance> datedOpenItems(@Param("type") SubLedgerType type,
                                        @Param("asOf") LocalDate asOf);
}
