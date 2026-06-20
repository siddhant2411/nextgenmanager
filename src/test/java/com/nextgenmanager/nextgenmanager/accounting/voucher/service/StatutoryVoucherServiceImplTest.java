package com.nextgenmanager.nextgenmanager.accounting.voucher.service;

import com.nextgenmanager.nextgenmanager.accounting.posting.LedgerResolver;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.DepreciationVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.PayrollVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatutoryVoucherServiceImplTest {

    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private StatutoryVoucherServiceImpl service;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    private BigDecimal sumDr(VoucherDraft d) {
        return d.getLines().stream().map(l -> l.getDrAmount() != null ? l.getDrAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCr(VoucherDraft d) {
        return d.getLines().stream().map(l -> l.getCrAmount() != null ? l.getCrAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void payroll_postsBalancedEntry() {
        LedgerAccount salaries = ledger(5030L), salaryPayable = ledger(9031L), pf = ledger(9017L),
                esi = ledger(9018L), pt = ledger(9019L), tds = ledger(9015L);
        lenient().when(ledgers.salariesAndWages()).thenReturn(salaries);
        lenient().when(ledgers.salaryPayable()).thenReturn(salaryPayable);
        lenient().when(ledgers.pfPayable()).thenReturn(pf);
        lenient().when(ledgers.esiPayable()).thenReturn(esi);
        lenient().when(ledgers.professionalTaxPayable()).thenReturn(pt);
        lenient().when(ledgers.tdsPayable()).thenReturn(tds);

        PayrollVoucherRequest req = new PayrollVoucherRequest();
        req.setDate(LocalDate.of(2025, 6, 30));
        req.setGrossSalary(new BigDecimal("100000"));
        req.setEmployeePf(new BigDecimal("1800"));
        req.setEmployerPf(new BigDecimal("1800"));
        req.setProfessionalTax(new BigDecimal("200"));
        req.setTds(new BigDecimal("5000"));

        service.postPayroll(req, "admin");

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("admin"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.PAYROLL);
        assertThat(d.getSourceDocType()).isEqualTo("PAYROLL");
        // Dr = gross + employer PF = 101800; balanced
        assertThat(sumDr(d)).isEqualByComparingTo("101800");
        assertThat(sumCr(d)).isEqualByComparingTo("101800");
        // Net salary payable = 100000 - 1800 - 200 - 5000 = 93000
        VoucherLineDraft net = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(9031L)).findFirst().orElseThrow();
        assertThat(net.getCrAmount()).isEqualByComparingTo("93000");
    }

    @Test
    void payroll_negativeNet_throws() {
        PayrollVoucherRequest req = new PayrollVoucherRequest();
        req.setDate(LocalDate.of(2025, 6, 30));
        req.setGrossSalary(new BigDecimal("1000"));
        req.setTds(new BigDecimal("5000"));

        assertThatThrownBy(() -> service.postPayroll(req, "admin"))
                .isInstanceOf(InvalidVoucherException.class);
        verify(postingService, never()).post(any(), any());
    }

    @Test
    void depreciation_postsDrExpenseCrAccumulated() {
        LedgerAccount depExpense = ledger(5070L), accumulated = ledger(1020L);
        when(ledgers.depreciationExpense()).thenReturn(depExpense);
        when(ledgers.accumulatedDepreciation()).thenReturn(accumulated);

        DepreciationVoucherRequest req = new DepreciationVoucherRequest();
        req.setDate(LocalDate.of(2025, 6, 30));
        req.setAmount(new BigDecimal("12000"));

        service.postDepreciation(req, "admin");

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("admin"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.DEPRECIATION);
        assertThat(d.getSourceDocType()).isEqualTo("DEPRECIATION");
        VoucherLineDraft dep = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(5070L)).findFirst().orElseThrow();
        assertThat(dep.getDrAmount()).isEqualByComparingTo("12000");
        VoucherLineDraft acc = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(1020L)).findFirst().orElseThrow();
        assertThat(acc.getCrAmount()).isEqualByComparingTo("12000");
    }
}
