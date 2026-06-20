package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.tds.events.TdsChallanDepositedEvent;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsChallan;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsChallanRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TdsChallanPostingListenerTest {

    @Mock private TdsChallanRepository challanRepo;
    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private TdsChallanPostingListener listener;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    @Test
    void challan_postsDrTdsPayable_crBank() {
        TdsChallan c = new TdsChallan();
        c.setId(77L);
        c.setChallanNumber("CIN12345");
        c.setFinancialYear("2025-26");
        c.setQuarter("Q1");
        c.setDepositDate(LocalDate.of(2025, 7, 7));
        c.setAmount(new BigDecimal("2500.00"));

        LedgerAccount tdsPayable = ledger(9015L), bank = ledger(4011L);
        when(challanRepo.findById(77L)).thenReturn(Optional.of(c));
        when(ledgers.tdsPayable()).thenReturn(tdsPayable);
        when(ledgers.bankPrimary()).thenReturn(bank);

        listener.onChallanDeposited(new TdsChallanDepositedEvent(77L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.PAYMENT);
        assertThat(d.getSourceDocType()).isEqualTo("TDS_CHALLAN");
        VoucherLineDraft drLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(9015L)).findFirst().orElseThrow();
        assertThat(drLine.getDrAmount()).isEqualByComparingTo("2500.00");
        VoucherLineDraft crLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(4011L)).findFirst().orElseThrow();
        assertThat(crLine.getCrAmount()).isEqualByComparingTo("2500.00");
    }
}
