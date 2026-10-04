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
        if (tds.signum() > 0 && dto.getTdsRate() != null && invoice.getGrandTotal() != null
                && invoice.getGrandTotal().signum() > 0 && invoice.getSubtotal() != null) {
            // TDS is on the value of the supply, not on the GST charged on top of it. The payment is
            // gross, so only the share of it that is the taxable value carries the deduction; working
            // on the whole payment deducted ~18% too much on a 18%-GST bill.
            BigDecimal taxableShare = invoice.getSubtotal().divide(invoice.getGrandTotal(), 8, java.math.RoundingMode.HALF_UP);
            tds = dto.getAmount().multiply(taxableShare).multiply(dto.getTdsRate())
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        }
        if (tds.compareTo(dto.getAmount()) > 0) {
            throw new IllegalArgumentException("TDS amount cannot exceed the payment amount");
        }

        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        BigDecimal alreadyPaid = paymentRepository.sumAmountByVendorInvoiceId(vendorInvoiceId);
        BigDecimal balance = invoice.getGrandTotal().subtract(alreadyPaid == null ? BigDecimal.ZERO : alreadyPaid);
        if (dto.getAmount().compareTo(balance.add(new BigDecimal("0.01"))) > 0) {
            throw new IllegalArgumentException("Payment of " + dto.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                    + " exceeds the balance due of " + balance.max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP));
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
