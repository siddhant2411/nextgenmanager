package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.SalesPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SalesPaymentRepository extends JpaRepository<SalesPayment, Long> {

    List<SalesPayment> findBySalesOrderIdOrderByPaymentDateAsc(Long salesOrderId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SalesPayment p WHERE p.salesOrder.id = :salesOrderId")
    BigDecimal sumAmountBySalesOrderId(Long salesOrderId);
}
