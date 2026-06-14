package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.VendorPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface VendorPaymentRepository extends JpaRepository<VendorPayment, Long> {

    List<VendorPayment> findByVendorInvoiceIdOrderByPaymentDateAsc(Long vendorInvoiceId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM VendorPayment p WHERE p.vendorInvoice.id = :invoiceId")
    BigDecimal sumAmountByVendorInvoiceId(Long invoiceId);
}
