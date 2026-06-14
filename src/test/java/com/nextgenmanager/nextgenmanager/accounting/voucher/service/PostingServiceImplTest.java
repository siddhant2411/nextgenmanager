package com.nextgenmanager.nextgenmanager.accounting.voucher.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.common.approval.dto.RequiredStep;
import com.nextgenmanager.nextgenmanager.common.approval.service.ApprovalEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostingServiceImplTest {

    @Mock private VoucherRepository voucherRepo;
    @Mock private LedgerAccountRepository ledgerRepo;
    @Mock private FinancialYearService fyService;
    @Mock private VoucherNumberGenerator numberGenerator;
    @Mock private ApprovalEngine approvalEngine;

    @InjectMocks private PostingServiceImpl service;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        lenient().when(la.getCode()).thenReturn("L-" + id);
        lenient().when(la.getName()).thenReturn("Ledger " + id);
        return la;
    }

    /** Common stubs for a successful balanced post. */
    private void primePostingInfra() {
        AccountingPeriod period = mock(AccountingPeriod.class);
        lenient().when(period.getFinancialYear()).thenReturn(mock(FinancialYear.class));
        lenient().when(period.getId()).thenReturn(10L);
        when(fyService.resolveOpenPeriod(any())).thenReturn(period);
        when(numberGenerator.next(any())).thenReturn("JNL/2526/0001");
        LedgerAccount la1 = ledger(1L), la2 = ledger(2L);
        when(ledgerRepo.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(la1));
        when(ledgerRepo.findByIdAndDeletedDateIsNull(2L)).thenReturn(Optional.of(la2));
        when(voucherRepo.save(any(Voucher.class))).thenAnswer(i -> i.getArgument(0));
    }

    private VoucherDraft balancedDraft() {
        VoucherLineDraft l1 = new VoucherLineDraft();
        l1.setLedgerAccountId(1L); l1.setDrAmount(new BigDecimal("100.00")); l1.setCrAmount(BigDecimal.ZERO);
        VoucherLineDraft l2 = new VoucherLineDraft();
        l2.setLedgerAccountId(2L); l2.setDrAmount(BigDecimal.ZERO); l2.setCrAmount(new BigDecimal("100.00"));
        VoucherDraft d = new VoucherDraft();
        d.setVoucherType(VoucherType.JOURNAL);
        d.setDate(LocalDate.of(2025, 6, 1));
        d.setLines(List.of(l1, l2));
        return d;
    }

    @Test
    void sourceDocAutoPost_bypassesApproval_evenIfPolicyWouldMatch() {
        primePostingInfra();
        VoucherDraft draft = balancedDraft();
        draft.setSourceDocType("TAX_INVOICE");
        draft.setSourceDocId(5L);
        when(voucherRepo.existsBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull("TAX_INVOICE", 5L)).thenReturn(false);

        VoucherDto result = service.post(draft, "SYSTEM");

        assertThat(result.getStatus()).isEqualTo(VoucherStatus.POSTED);
        // The MANUAL_VOUCHER gate must never be consulted for source-document posts.
        verify(approvalEngine, never()).evaluate(anyString(), anyMap());
        verify(approvalEngine, never()).submit(anyString(), any(), anyMap(), anyString());
    }

    @Test
    void manualVoucher_withNoMatchingRule_postsDirectly() {
        primePostingInfra();
        when(approvalEngine.evaluate(eq("MANUAL_VOUCHER"), anyMap())).thenReturn(List.of());

        VoucherDto result = service.post(balancedDraft(), "accountant");

        assertThat(result.getStatus()).isEqualTo(VoucherStatus.POSTED);
        verify(approvalEngine).evaluate(eq("MANUAL_VOUCHER"), anyMap());
    }

    @Test
    void manualVoucher_withMatchingRule_goesToPendingApproval() {
        primePostingInfra();
        when(approvalEngine.evaluate(eq("MANUAL_VOUCHER"), anyMap()))
                .thenReturn(List.of(mock(RequiredStep.class)));

        VoucherDto result = service.post(balancedDraft(), "accountant");

        assertThat(result.getStatus()).isEqualTo(VoucherStatus.PENDING_APPROVAL);
        verify(approvalEngine).submit(eq("MANUAL_VOUCHER"), any(), anyMap(), eq("accountant"));
    }
}
