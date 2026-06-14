package com.nextgenmanager.nextgenmanager.accounting.opening.repository;

import com.nextgenmanager.nextgenmanager.accounting.opening.model.OpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OpeningBalanceRepository extends JpaRepository<OpeningBalance, Long> {

    List<OpeningBalance> findByOpeningDateAndDeletedDateIsNull(LocalDate openingDate);

    boolean existsByOpeningDateAndDeletedDateIsNull(LocalDate openingDate);
}
