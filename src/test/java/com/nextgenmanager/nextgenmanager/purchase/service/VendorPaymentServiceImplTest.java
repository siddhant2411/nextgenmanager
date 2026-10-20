package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorPaymentCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorPayment;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorPaymentServiceImplTest {

    @Mock private VendorPaymentRepository paymentRepository;
    @Mock private VendorInvoiceRepository invoiceRepository;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private AuditTrailService auditTrail;

    private VendorPaymentServiceImpl service;
    private VendorInvoice invoice;

    @BeforeEach
    void setUp() {
        service = new VendorPaymentServiceImpl(paymentRepository, invoiceRepository, domainEventPublisher, auditTrail);
        invoice = new VendorInvoice();
        invoice.setInvoiceNumber("PCF/26-27/041A");
        invoice.setSubtotal(new BigDecimal("50000.00"));
        invoice.setGrandTotal(new BigDecimal("59000.00"));
        lenient().when(invoiceRepository.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(invoice));
    }

    private VendorPaymentCreateDto payment(String amount) {
        VendorPaymentCreateDto dto = new VendorPaymentCreateDto();
        dto.setPaymentDate(LocalDate.of(2026, 4, 30));
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }

    @Test
    void aPaymentLargerThanTheBalanceIsRefused() {
        when(paymentRepository.sumAmountByVendorInvoiceId(1L)).thenReturn(new BigDecimal("40000.00"));

        assertThatThrownBy(() -> service.recordPayment(1L, payment("20000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the balance due of 19000.00");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void thePaymentThatClearsTheBalanceExactlyIsAccepted() {
        when(paymentRepository.sumAmountByVendorInvoiceId(1L)).thenReturn(new BigDecimal("40000.00"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordPayment(1L, payment("19000.00"));

        verify(paymentRepository).save(any());
    }

    @Test
    void aZeroPaymentIsRefused() {
        assertThatThrownBy(() -> service.recordPayment(1L, payment("0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void tdsIsDeductedOnTheTaxableShareOfAGstInclusivePayment() {
        // ₹59,000 paid on a ₹50,000 + 18% GST bill under a 2% section: TDS is 2% of 50,000, not of 59,000.
        when(paymentRepository.sumAmountByVendorInvoiceId(1L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        VendorPaymentCreateDto dto = payment("59000.00");
        dto.setTdsSectionCode("194C");
        dto.setTdsRate(new BigDecimal("2"));
        dto.setTdsAmount(new BigDecimal("1180.00")); // what a client computing on the gross would send

        service.recordPayment(1L, dto);

        ArgumentCaptor<VendorPayment> saved = ArgumentCaptor.forClass(VendorPayment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getTdsAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void deletingAPaymentKeepsAFullCopyInTheAuditTrailBeforeTheRowGoes() {
        VendorPayment paid = new VendorPayment();
        paid.setVendorInvoice(invoice);
        paid.setPaymentDate(LocalDate.of(2026, 5, 2));
        paid.setAmount(new BigDecimal("25000.00"));
        paid.setPaymentMode(PaymentMode.values()[0]);
        paid.setReferenceNumber("UTR 5521");
        paid.setTdsAmount(new BigDecimal("500.00"));
        paid.setCreatedBy("meera");
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(paid));

        service.deletePayment(7L);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        var order = inOrder(auditTrail, paymentRepository);
        order.verify(auditTrail).record(event.capture());
        order.verify(paymentRepository).delete(paid);
        AuditEvent e = event.getValue();
        assertThat(e.getAction()).isEqualTo(AuditActions.DELETE);
        assertThat(e.getEntityType()).isEqualTo(SourceDocTypes.VENDOR_PAYMENT);
        assertThat(e.getEntityId()).isEqualTo(7L);
        assertThat(e.getLabel()).contains("PCF/26-27/041A");
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) e.getDetails();
        assertThat(copy).containsEntry("amount", new BigDecimal("25000.00"))
                .containsEntry("referenceNumber", "UTR 5521")
                .containsEntry("tdsAmount", new BigDecimal("500.00"))
                .containsEntry("recordedBy", "meera")
                .containsEntry("paymentDate", "2026-05-02");
    }
}
