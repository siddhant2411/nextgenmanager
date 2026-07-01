package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentDto;
import com.nextgenmanager.nextgenmanager.purchase.events.VendorPaymentMadeEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorPayment;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VendorPaymentServiceImpl implements VendorPaymentService {

    private final VendorPaymentRepository paymentRepository;
    private final VendorInvoiceRepository invoiceRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public VendorPaymentDto recordPayment(Long vendorInvoiceId, VendorPaymentCreateDto dto) {
        VendorInvoice invoice = invoiceRepository.findByIdAndDeletedDateIsNull(vendorInvoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor invoice not found: " + vendorInvoiceId));

        BigDecimal tds = dto.getTdsAmount() != null ? dto.getTdsAmount() : BigDecimal.ZERO;
        if (tds.compareTo(dto.getAmount()) > 0) {
            throw new IllegalArgumentException("TDS amount cannot exceed the payment amount");
        }

        VendorPayment payment = new VendorPayment();
        payment.setVendorInvoice(invoice);
        payment.setPaymentDate(dto.getPaymentDate());
        payment.setAmount(dto.getAmount());
        payment.setPaymentMode(dto.getPaymentMode());
        payment.setReferenceNumber(dto.getReferenceNumber());
        payment.setNotes(dto.getNotes());
        payment.setTdsSectionCode(tds.signum() > 0 ? dto.getTdsSectionCode() : null);
        payment.setTdsRate(tds.signum() > 0 ? dto.getTdsRate() : null);
        payment.setTdsAmount(tds);
        payment.setCreatedBy(currentUser());

        VendorPayment saved = paymentRepository.save(payment);

        // Accounting auto-posts the PAYMENT voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new VendorPaymentMadeEvent(saved.getId()));
        return toDto(saved, invoice.getInvoiceNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorPaymentDto> getPaymentsForInvoice(Long vendorInvoiceId) {
        return paymentRepository.findByVendorInvoiceIdOrderByPaymentDateAsc(vendorInvoiceId)
                .stream()
                .map(p -> toDto(p, p.getVendorInvoice().getInvoiceNumber()))
                .toList();
    }

    @Override
    public void deletePayment(Long paymentId) {
        VendorPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        paymentRepository.delete(payment);

        // Accounting reverses the PAYMENT voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DocumentVoidedEvent(SourceDocTypes.VENDOR_PAYMENT, paymentId, "Payment deleted"));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalPaidForInvoice(Long vendorInvoiceId) {
        return paymentRepository.sumAmountByVendorInvoiceId(vendorInvoiceId);
    }

    private VendorPaymentDto toDto(VendorPayment p, String invoiceNumber) {
        VendorPaymentDto dto = new VendorPaymentDto();
        dto.setId(p.getId());
        dto.setVendorInvoiceId(p.getVendorInvoice().getId());
        dto.setInvoiceNumber(invoiceNumber);
        dto.setPaymentDate(p.getPaymentDate());
        dto.setAmount(p.getAmount());
        dto.setPaymentMode(p.getPaymentMode());
        dto.setReferenceNumber(p.getReferenceNumber());
        dto.setNotes(p.getNotes());
        dto.setTdsSectionCode(p.getTdsSectionCode());
        dto.setTdsRate(p.getTdsRate());
        dto.setTdsAmount(p.getTdsAmount());
        dto.setCreationDate(p.getCreationDate());
        return dto;
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
