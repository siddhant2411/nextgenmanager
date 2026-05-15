package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, Long> {

    List<TaxInvoice> findBySalesOrderIdAndDeletedDateIsNull(Long salesOrderId);

    Page<TaxInvoice> findByDeletedDateIsNull(Pageable pageable);

    Page<TaxInvoice> findByStatusAndDeletedDateIsNull(TaxInvoiceStatus status, Pageable pageable);

    Optional<TaxInvoice> findByIdAndDeletedDateIsNull(Long id);
}
