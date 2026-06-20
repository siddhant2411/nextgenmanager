package com.nextgenmanager.nextgenmanager.accounting.tds.repository;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsChallan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TdsChallanRepository extends JpaRepository<TdsChallan, Long> {

    List<TdsChallan> findByFinancialYearAndDeletedDateIsNullOrderByDepositDateDesc(String financialYear);

    List<TdsChallan> findByDeletedDateIsNullOrderByDepositDateDesc();
}
