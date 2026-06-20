package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.events.TdsChallanDepositedEvent;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsChallan;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntryStatus;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsSection;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsChallanRepository;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsEntryRepository;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TdsChallanServiceImplTest {

    @Mock private TdsChallanRepository challanRepo;
    @Mock private TdsEntryRepository entryRepo;
    @Mock private DomainEventPublisher publisher;

    @InjectMocks private TdsChallanServiceImpl service;

    private TdsEntry entry(long id, String amount) {
        TdsSection s = new TdsSection();
        s.setSection("194C");
        TdsEntry e = new TdsEntry();
        e.setId(id);
        e.setSection(s);
        e.setTdsAmount(new BigDecimal(amount));
        e.setStatus(TdsEntryStatus.DEDUCTED);
        return e;
    }

    private TdsChallanCreateDto dto() {
        TdsChallanCreateDto d = new TdsChallanCreateDto();
        d.setFinancialYear("2025-26");
        d.setQuarter("Q1");
        d.setChallanNumber("CIN12345");
        d.setBsrCode("0510308");
        d.setDepositDate(LocalDate.of(2025, 7, 7));
        return d;
    }

    @Test
    void createChallan_sumsPending_flipsToDeposited_publishesEvent() {
        List<TdsEntry> pending = List.of(entry(1L, "2000.00"), entry(2L, "500.00"));
        when(entryRepo.findByFinancialYearAndQuarterAndStatusAndDeletedDateIsNull("2025-26", "Q1", TdsEntryStatus.DEDUCTED))
                .thenReturn(pending);
        when(challanRepo.save(any())).thenAnswer(inv -> {
            TdsChallan c = inv.getArgument(0);
            c.setId(77L);
            return c;
        });

        TdsChallanDto result = service.createChallan(dto(), "admin");

        assertThat(result.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(result.getEntryCount()).isEqualTo(2);
        assertThat(pending).allMatch(e -> e.getStatus() == TdsEntryStatus.DEPOSITED);
        assertThat(pending).allMatch(e -> e.getChallanId() == 77L);
        verify(entryRepo).saveAll(pending);

        ArgumentCaptor<TdsChallanDepositedEvent> cap = ArgumentCaptor.forClass(TdsChallanDepositedEvent.class);
        verify(publisher).publish(cap.capture());
        assertThat(cap.getValue().getChallanId()).isEqualTo(77L);
    }

    @Test
    void noPendingEntries_throws() {
        when(entryRepo.findByFinancialYearAndQuarterAndStatusAndDeletedDateIsNull("2025-26", "Q1", TdsEntryStatus.DEDUCTED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.createChallan(dto(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No pending TDS");
        verify(publisher, never()).publish(any());
    }
}
