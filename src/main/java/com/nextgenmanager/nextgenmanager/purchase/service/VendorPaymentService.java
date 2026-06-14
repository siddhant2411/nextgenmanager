package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentDto;

import java.math.BigDecimal;
import java.util.List;

public interface VendorPaymentService {

    VendorPaymentDto recordPayment(Long vendorInvoiceId, VendorPaymentCreateDto dto);

    List<VendorPaymentDto> getPaymentsForInvoice(Long vendorInvoiceId);

    void deletePayment(Long paymentId);

    BigDecimal getTotalPaidForInvoice(Long vendorInvoiceId);
}
