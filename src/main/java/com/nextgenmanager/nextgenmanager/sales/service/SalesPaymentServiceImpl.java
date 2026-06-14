package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.sales.events.SalesPaymentReceivedEvent;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesPaymentDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesPayment;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesPaymentServiceImpl implements SalesPaymentService {

    private final SalesPaymentRepository paymentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public SalesPaymentDto recordPayment(Long salesOrderId, SalesPaymentCreateDto dto) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sales order not found: " + salesOrderId));

        SalesPayment payment = new SalesPayment();
        payment.setSalesOrder(order);
        payment.setPaymentDate(dto.getPaymentDate());
        payment.setAmount(dto.getAmount());
        payment.setPaymentMode(dto.getPaymentMode());
        payment.setReferenceNumber(dto.getReferenceNumber());
        payment.setNotes(dto.getNotes());

        SalesPayment saved = paymentRepository.save(payment);

        // Accounting auto-posts the RECEIPT voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new SalesPaymentReceivedEvent(saved.getId()));
        return toDto(saved, order.getOrderNumber());
    }

    @Override
    public List<SalesPaymentDto> getPaymentsForOrder(Long salesOrderId) {
        return paymentRepository.findBySalesOrderIdOrderByPaymentDateAsc(salesOrderId)
                .stream()
                .map(p -> toDto(p, p.getSalesOrder().getOrderNumber()))
                .toList();
    }

    @Override
    public void deletePayment(Long paymentId) {
        SalesPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        paymentRepository.delete(payment);

        // Accounting reverses the RECEIPT voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DocumentVoidedEvent(SourceDocTypes.SALES_PAYMENT, paymentId, "Payment deleted"));
    }

    @Override
    public BigDecimal getTotalPaidForOrder(Long salesOrderId) {
        return paymentRepository.sumAmountBySalesOrderId(salesOrderId);
    }

    private SalesPaymentDto toDto(SalesPayment p, String orderNumber) {
        SalesPaymentDto dto = new SalesPaymentDto();
        dto.setId(p.getId());
        dto.setSalesOrderId(p.getSalesOrder().getId());
        dto.setOrderNumber(orderNumber);
        dto.setPaymentDate(p.getPaymentDate());
        dto.setAmount(p.getAmount());
        dto.setPaymentMode(p.getPaymentMode());
        dto.setReferenceNumber(p.getReferenceNumber());
        dto.setNotes(p.getNotes());
        dto.setCreationDate(p.getCreationDate());
        return dto;
    }
}
