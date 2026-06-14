package com.nextgenmanager.nextgenmanager.accounting.coa.repository;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {

    Optional<LedgerAccount> findByIdAndDeletedDateIsNull(Long id);

    Optional<LedgerAccount> findByCodeAndDeletedDateIsNull(String code);

    List<LedgerAccount> findByDeletedDateIsNullOrderByCode();

    /** Trial Balance: JOIN FETCH group so la.getGroup().getName() is safe outside transaction. */
    @Query("SELECT la FROM LedgerAccount la JOIN FETCH la.group WHERE la.deletedDate IS NULL ORDER BY la.code")
    List<LedgerAccount> findAllWithGroupOrderByCode();

    List<LedgerAccount> findByGroupIdAndDeletedDateIsNull(Long groupId);

    List<LedgerAccount> findBySubLedgerTypeAndDeletedDateIsNull(SubLedgerType subLedgerType);

    Optional<LedgerAccount> findByContactIdAndSubLedgerTypeAndDeletedDateIsNull(int contactId, SubLedgerType subLedgerType);

    List<LedgerAccount> findByContactIdAndDeletedDateIsNull(int contactId);

    boolean existsByCodeAndDeletedDateIsNull(String code);

    /** Search by name or code for autocomplete in voucher entry. */
    @Query("SELECT la FROM LedgerAccount la WHERE la.deletedDate IS NULL AND " +
           "(LOWER(la.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(la.code) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY la.code")
    List<LedgerAccount> search(@Param("q") String query);
}
