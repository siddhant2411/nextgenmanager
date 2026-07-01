package com.nextgenmanager.nextgenmanager.accounting.tds.repository;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TdsEntryRepository extends JpaRepository<TdsEntry, Long> {

    Optional<TdsEntry> findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(String sourceDocType, Long sourceDocId);

    /** Fetches section + contact so deductee rows render without lazy hits. */
    @Query("SELECT e FROM TdsEntry e JOIN FETCH e.section JOIN FETCH e.contact " +
           "WHERE e.financialYear = :fy AND e.quarter = :quarter AND e.deletedDate IS NULL " +
           "ORDER BY e.deductionDate ASC, e.id ASC")
    List<TdsEntry> findForQuarter(String fy, String quarter);

    List<TdsEntry> findByFinancialYearAndQuarterAndStatusAndDeletedDateIsNull(
            String financialYear, String quarter, TdsEntryStatus status);

    List<TdsEntry> findByChallanIdAndDeletedDateIsNull(Long challanId);
}
