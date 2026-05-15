package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, Long> {

    List<VendorInvoice> findByPurchaseOrderIdAndDeletedDateIsNull(Long poId);

    Optional<VendorInvoice> findByIdAndDeletedDateIsNull(Long id);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
