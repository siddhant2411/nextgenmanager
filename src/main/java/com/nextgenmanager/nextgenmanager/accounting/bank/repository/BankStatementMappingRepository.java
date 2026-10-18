package com.nextgenmanager.nextgenmanager.accounting.bank.repository;

import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankStatementMappingRepository extends JpaRepository<BankStatementMapping, Long> {
    Optional<BankStatementMapping> findByLedgerAccountId(Long ledgerAccountId);
}
