package com.nextgenmanager.nextgenmanager.accounting.period.repository;

import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FinancialYearRepository extends JpaRepository<FinancialYear, Long> {

    Optional<FinancialYear> findFirstByStatus(FyStatus status);

    Optional<FinancialYear> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate d1, LocalDate d2);
}
