package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceDocReversalListenerTest {

    @Mock private VoucherRepository voucherRepo;
    @Mock private PostingService postingService;
    @Mock private AuditTrailService auditTrail;

    @InjectMocks private SourceDocReversalListener listener;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Voucher voucher(VoucherStatus status) {
        Voucher v = mock(Voucher.class);
        lenient().when(v.getId()).thenReturn(500L);
        lenient().when(v.getStatus()).thenReturn(status);
        lenient().when(v.getVoucherNumber()).thenReturn("SAL/2526/0001");
        return v;
    }

    @Test
    void reversesPostedVoucher() {
        Voucher posted = voucher(VoucherStatus.POSTED);
        when(voucherRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(SourceDocTypes.TAX_INVOICE, 9L))
                .thenReturn(Optional.of(posted));
        when(postingService.reverse(eq(500L), anyString(), eq("SYSTEM"))).thenReturn(new VoucherDto());

        listener.onDocumentVoided(new DocumentVoidedEvent(SourceDocTypes.TAX_INVOICE, 9L, "Invoice cancelled"));

        verify(postingService).reverse(eq(500L), eq("Invoice cancelled"), eq("SYSTEM"));
    }

    @Test
    void noVoucherForSource_isNoOp() {
        when(voucherRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(SourceDocTypes.VENDOR_PAYMENT, 9L))
                .thenReturn(Optional.empty());

        listener.onDocumentVoided(new DocumentVoidedEvent(SourceDocTypes.VENDOR_PAYMENT, 9L, "Payment deleted"));

        verify(postingService, never()).reverse(anyLong(), anyString(), anyString());
    }

    @Test
    void alreadyReversedVoucher_isNoOp() {
        Voucher reversed = voucher(VoucherStatus.REVERSED);
        when(voucherRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(SourceDocTypes.TAX_INVOICE, 9L))
                .thenReturn(Optional.of(reversed));

        listener.onDocumentVoided(new DocumentVoidedEvent(SourceDocTypes.TAX_INVOICE, 9L, "Invoice cancelled"));

        verify(postingService, never()).reverse(anyLong(), anyString(), anyString());
    }

    @Test
    void reversesInTheNameOfWhoeverCancelledTheDocument() {
        // The event is raised inside the request, where the user is signed in; the listener runs later.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("meera", null, List.of()));
        DocumentVoidedEvent event = new DocumentVoidedEvent(SourceDocTypes.TAX_INVOICE, 9L, "Invoice cancelled");
        SecurityContextHolder.clearContext();

        Voucher posted = voucher(VoucherStatus.POSTED);
        when(voucherRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(SourceDocTypes.TAX_INVOICE, 9L))
                .thenReturn(Optional.of(posted));
        when(postingService.reverse(eq(500L), anyString(), eq("meera"))).thenReturn(new VoucherDto());

        listener.onDocumentVoided(event);

        verify(postingService).reverse(eq(500L), eq("Invoice cancelled"), eq("meera"));
        ArgumentCaptor<AuditEvent> recorded = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrail).recordInNewTransaction(recorded.capture());
        assertThat(recorded.getValue().getAction()).isEqualTo(AuditActions.VOID);
        assertThat(recorded.getValue().getUsername()).isEqualTo("meera");
        assertThat(recorded.getValue().getVoucherId()).isEqualTo(500L);
    }

    @Test
    void aFailedReversalIsRecordedSoItCanBeShownAndPutRight() {
        Voucher posted = voucher(VoucherStatus.POSTED);
        when(voucherRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(SourceDocTypes.VENDOR_INVOICE, 4L))
                .thenReturn(Optional.of(posted));
        when(postingService.reverse(anyLong(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("period is LOCKED"));

        listener.onDocumentVoided(new DocumentVoidedEvent(SourceDocTypes.VENDOR_INVOICE, 4L, "Vendor invoice cancelled"));

        ArgumentCaptor<AuditEvent> recorded = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrail, times(2)).recordInNewTransaction(recorded.capture());
        AuditEvent failure = recorded.getAllValues().get(1);
        assertThat(failure.getAction()).isEqualTo(AuditActions.AUTO_REVERSAL_FAILED);
        assertThat(failure.getVoucherId()).isEqualTo(500L);
        assertThat(failure.getLabel()).isEqualTo("SAL/2526/0001");
        assertThat(failure.getSummary()).contains("period is LOCKED");
        assertThat(failure.getUsername()).isEqualTo("SYSTEM");
    }
}
