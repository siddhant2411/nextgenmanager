package com.nextgenmanager.nextgenmanager.accounting.coa.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.dto.LedgerAccountCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountGroup;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.AccountGroupRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every change to a ledger reaches the audit trail — above all an opening balance, which moves every report. */
@ExtendWith(MockitoExtension.class)
class CoaServiceImplAuditTest {

    @Mock private AccountGroupRepository groupRepo;
    @Mock private LedgerAccountRepository ledgerRepo;
    @Mock private VoucherLineRepository voucherLineRepo;
    @Mock private ContactRepository contactRepo;
    @Mock private AuditTrailService auditTrail;

    private CoaServiceImpl service;
    private AccountGroup bankGroup;
    private LedgerAccount bank;

    @BeforeEach
    void setUp() {
        service = new CoaServiceImpl(groupRepo, ledgerRepo, voucherLineRepo, contactRepo, auditTrail);
        bankGroup = new AccountGroup();
        bankGroup.setId(23L);
        bankGroup.setCode("CCE");
        bankGroup.setName("Cash & Cash Equivalents");
        bankGroup.setNature(AccountNature.ASSET);

        bank = new LedgerAccount();
        bank.setId(12L);
        bank.setCode("4011");
        bank.setName("Bank Account - Primary");
        bank.setGroup(bankGroup);
        bank.setNature(AccountNature.ASSET);
        bank.setOpeningBalance(new BigDecimal("1875000.00"));
        bank.setOpeningBalanceDrCr("DR");
        bank.setSubLedgerType(SubLedgerType.BANK);
        bank.setBankAccount(true);
        bank.setReconcilable(true);

        when(ledgerRepo.findByIdAndDeletedDateIsNull(12L)).thenReturn(Optional.of(bank));
        when(groupRepo.findById(23L)).thenReturn(Optional.of(bankGroup));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** The form as the screen sends it back for this ledger, with nothing changed. */
    private LedgerAccountCreateDto unchanged() {
        LedgerAccountCreateDto dto = new LedgerAccountCreateDto();
        dto.setCode("4011");
        dto.setName("Bank Account - Primary");
        dto.setGroupId(23L);
        dto.setOpeningBalance(new BigDecimal("1875000"));
        dto.setOpeningBalanceDrCr("DR");
        dto.setSubLedgerType(SubLedgerType.BANK);
        dto.setBankAccount(true);
        dto.setReconcilable(true);
        return dto;
    }

    @Test
    void aChangedOpeningBalanceIsRecordedWithTheOldAndNewFigures() {
        LedgerAccountCreateDto dto = unchanged();
        dto.setOpeningBalance(new BigDecimal("1975000"));

        service.updateLedger(12L, dto);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrail).record(event.capture());
        AuditEvent e = event.getValue();
        assertThat(e.getAction()).isEqualTo(AuditActions.UPDATE);
        assertThat(e.getEntityType()).isEqualTo(AuditActions.LEDGER);
        assertThat(e.getEntityId()).isEqualTo(12L);
        assertThat(e.getLabel()).isEqualTo("4011 Bank Account - Primary");
        @SuppressWarnings("unchecked")
        Map<String, Object> changed = (Map<String, Object>) e.getDetails();
        assertThat(changed).containsOnlyKeys("openingBalance");
        @SuppressWarnings("unchecked")
        Map<String, Object> pair = (Map<String, Object>) changed.get("openingBalance");
        assertThat((BigDecimal) pair.get("from")).isEqualByComparingTo("1875000");
        assertThat((BigDecimal) pair.get("to")).isEqualByComparingTo("1975000");
    }

    @Test
    void untickingBankAccountIsRecorded() {
        LedgerAccountCreateDto dto = unchanged();
        dto.setBankAccount(false);

        service.updateLedger(12L, dto);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrail).record(event.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> changed = (Map<String, Object>) event.getValue().getDetails();
        assertThat(changed).containsOnlyKeys("bankAccount");
    }

    @Test
    void theFormFillingInDrForALedgerWithNoBalanceIsNotAChange() {
        // Seen live: a zero-balance ledger stored with no side; the form sends "DR" back on every save.
        bank.setOpeningBalance(BigDecimal.ZERO);
        bank.setOpeningBalanceDrCr(null);
        LedgerAccountCreateDto dto = unchanged();
        dto.setOpeningBalance(BigDecimal.ZERO);
        dto.setOpeningBalanceDrCr("DR");

        service.updateLedger(12L, dto);

        verify(auditTrail, never()).record(any());
    }

    @Test
    void savingWithoutAChangeWritesNothing() {
        service.updateLedger(12L, unchanged());

        verify(auditTrail, never()).record(any());
    }
}
