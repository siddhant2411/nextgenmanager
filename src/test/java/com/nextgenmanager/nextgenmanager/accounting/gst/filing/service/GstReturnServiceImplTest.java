package com.nextgenmanager.nextgenmanager.accounting.gst.filing.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.filing.dto.GstReturnDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturn;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturnType;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.repository.GstReturnRepository;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service.Gstr1Service;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.GstReconciliation;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.Gstr3bDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.TaxAmounts;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.service.Gstr3bService;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.AccountingPeriodRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GstReturnServiceImplTest {

    @Mock private GstReturnRepository returnRepo;
    @Mock private AccountingPeriodRepository periodRepo;
    @Mock private FinancialYearService financialYearService;
    @Mock private Gstr1Service gstr1Service;
    @Mock private Gstr3bService gstr3bService;

    @InjectMocks private GstReturnServiceImpl service;

    private AccountingPeriod period(PeriodStatus status) {
        FinancialYear fy = new FinancialYear();
        fy.setId(7L);
        AccountingPeriod p = new AccountingPeriod();
        p.setId(1L);
        p.setFinancialYear(fy);
        p.setPeriodNumber(2);
        p.setStartDate(LocalDate.of(2025, 5, 1));
        p.setEndDate(LocalDate.of(2025, 5, 31));
        p.setStatus(status);
        return p;
    }

    private Gstr3bDto sample3b() {
        TaxAmounts outward = new TaxAmounts(new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("90.00"), new BigDecimal("90.00"), BigDecimal.ZERO);
        TaxAmounts zero = TaxAmounts.zero();
        GstReconciliation rec = new GstReconciliation(
                new BigDecimal("180.00"), new BigDecimal("180.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        return new Gstr3bDto("24XXX", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31),
                outward, zero, java.util.List.of(), zero, zero, zero, zero, zero, rec);
    }

    @Test
    void fileGstr3b_persistsSnapshot_andLocksPeriod() {
        when(periodRepo.findById(1L)).thenReturn(Optional.of(period(PeriodStatus.OPEN)));
        when(returnRepo.findByPeriodIdAndReturnTypeAndDeletedDateIsNull(1L, GstReturnType.GSTR3B))
                .thenReturn(Optional.empty());
        when(gstr3bService.build(any(), any())).thenReturn(sample3b());
        when(gstr3bService.toJson(any())).thenReturn("{\"gstin\":\"24XXX\"}".getBytes());
        when(returnRepo.save(any(GstReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        GstReturnDto dto = service.fileGstr3b(1L, "head.user");

        assertThat(dto.returnType()).isEqualTo("GSTR3B");
        assertThat(dto.status()).isEqualTo("FILED");
        assertThat(dto.taxableValue()).isEqualByComparingTo("1000.00");
        assertThat(dto.total()).isEqualByComparingTo("180.00");   // sum of tax heads
        assertThat(dto.filedBy()).isEqualTo("head.user");
        assertThat(dto.payload()).isNull();                        // omitted on the action response

        verify(returnRepo).save(any(GstReturn.class));
        verify(financialYearService).lockPeriod(1L);               // 3B filing locks the month
    }

    @Test
    void fileGstr3b_blockedWhenPeriodAlreadyLocked() {
        when(periodRepo.findById(1L)).thenReturn(Optional.of(period(PeriodStatus.LOCKED)));

        assertThatThrownBy(() -> service.fileGstr3b(1L, "head.user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unlock");

        verify(returnRepo, never()).save(any());
        verify(financialYearService, never()).lockPeriod(eq(1L));
    }

    @Test
    void fileGstr3b_supersedesPriorReturn_bySoftDelete() {
        GstReturn prior = new GstReturn();
        when(periodRepo.findById(1L)).thenReturn(Optional.of(period(PeriodStatus.OPEN)));
        when(returnRepo.findByPeriodIdAndReturnTypeAndDeletedDateIsNull(1L, GstReturnType.GSTR3B))
                .thenReturn(Optional.of(prior));
        when(gstr3bService.build(any(), any())).thenReturn(sample3b());
        when(gstr3bService.toJson(any())).thenReturn("{}".getBytes());
        when(returnRepo.save(any(GstReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        service.fileGstr3b(1L, "head.user");

        assertThat(prior.getDeletedDate()).isNotNull();   // prior filing superseded
        verify(returnRepo, times(2)).save(any(GstReturn.class)); // prior (soft-delete) + new
    }
}
