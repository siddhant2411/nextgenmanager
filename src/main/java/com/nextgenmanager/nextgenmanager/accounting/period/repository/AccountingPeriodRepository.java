package com.nextgenmanager.nextgenmanager.accounting.period.repository;

import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findByFinancialYearIdOrderByPeriodNumber(Long fyId);

    @Query("SELECT p FROM AccountingPeriod p WHERE p.startDate <= :date AND p.endDate >= :date")
    Optional<AccountingPeriod> findByDate(@Param("date") LocalDate date);

    List<AccountingPeriod> findByFinancialYearIdAndStatus(Long fyId, PeriodStatus status);
}
