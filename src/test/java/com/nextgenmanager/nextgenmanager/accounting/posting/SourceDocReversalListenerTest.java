package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceDocReversalListenerTest {

    @Mock private VoucherRepository voucherRepo;
    @Mock private PostingService postingService;

    @InjectMocks private SourceDocReversalListener listener;

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
}
