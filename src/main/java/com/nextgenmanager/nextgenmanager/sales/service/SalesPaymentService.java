package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.SalesPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesPaymentDto;

import java.math.BigDecimal;
import java.util.List;

public interface SalesPaymentService {
    SalesPaymentDto recordPayment(Long salesOrderId, SalesPaymentCreateDto dto);
    List<SalesPaymentDto> getPaymentsForOrder(Long salesOrderId);
    void deletePayment(Long paymentId);
    BigDecimal getTotalPaidForOrder(Long salesOrderId);
}
