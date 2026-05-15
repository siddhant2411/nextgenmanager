package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    List<SalesOrder> findByStatusInAndDeletedDateIsNull(List<SalesOrderStatus> statuses);

    @Query("SELECT so FROM SalesOrder so WHERE so.deliveryDate < :today " +
           "AND so.status NOT IN ('FULLY_DISPATCHED','INVOICED','COMPLETED','CANCELLED') " +
           "AND so.deletedDate IS NULL")
    List<SalesOrder> findOverdue(LocalDate today);

    @Query("SELECT COUNT(so) FROM SalesOrder so WHERE so.status = :status AND so.deletedDate IS NULL")
    long countByStatusAndNotDeleted(SalesOrderStatus status);
}
