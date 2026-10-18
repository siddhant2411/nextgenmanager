package com.nextgenmanager.nextgenmanager.accounting.bank.service;

import com.nextgenmanager.nextgenmanager.accounting.bank.dto.BankReconciliationStatementDto;
import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementLine;
import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementMapping;
import com.nextgenmanager.nextgenmanager.accounting.bank.repository.BankStatementLineRepository;
import com.nextgenmanager.nextgenmanager.accounting.bank.repository.BankStatementMappingRepository;
import com.nextgenmanager.nextgenmanager.accounting.bank.service.BankReconciliationServiceImpl.BookCandidate;
import com.nextgenmanager.nextgenmanager.accounting.bank.service.BankReconciliationServiceImpl.StatementCandidate;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherLine;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankReconciliationServiceImplTest {

    @Mock private LedgerAccountRepository ledgerRepo;
    @Mock private BankStatementMappingRepository mappingRepo;
    @Mock private BankStatementLineRepository lineRepo;
    @Mock private VoucherLineRepository voucherLineRepo;

    @InjectMocks private BankReconciliationServiceImpl service;

    private static final LocalDate D = LocalDate.of(2026, 4, 10);
    private LedgerAccount bank;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        bank = new LedgerAccount();
        bank.setId(4011L);
        bank.setCode("4011");
        bank.setName("Bank Account - Primary");
        bank.setBankAccount(true);
        lenient().when(ledgerRepo.findByIdAndDeletedDateIsNull(4011L)).thenReturn(Optional.of(bank));
        lenient().when(mappingRepo.findByLedgerAccountId(4011L)).thenReturn(Optional.empty());
    }

    // ─── auto-match pairing ──────────────────────────────────────────────────

    @Test
    void pair_sameAmountWithinAWeek_matches_differentAmountOrTooFar_doesNot() {
        List<long[]> pairs = BankReconciliationServiceImpl.pair(
                List.of(new StatementCandidate(1L, D, amt("-25000"), null),
                        new StatementCandidate(2L, D, amt("500"), null)),
                List.of(new BookCandidate(10L, D.minusDays(3), amt("-25000"), "PAY/2627/00012"),
                        new BookCandidate(11L, D.minusDays(20), amt("500"), "REC/2627/00003"),   // too far back
                        new BookCandidate(12L, D, amt("-2500"), "PAY/2627/00013")));              // wrong amount

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0)).containsExactly(1L, 10L);
    }

    @Test
    void pair_twoEqualCandidates_theReferenceDecides_elseTheNearerDate_elseNobody() {
        List<BookCandidate> books = List.of(
                new BookCandidate(10L, D.minusDays(2), amt("-10000"), "PAY/1 cheque 000123 Sharma"),
                new BookCandidate(11L, D.minusDays(2), amt("-10000"), "PAY/2 cheque 000124 Verma"));

        assertThat(BankReconciliationServiceImpl.pair(
                List.of(new StatementCandidate(1L, D, amt("-10000"), "000124")), books))
                .singleElement().satisfies(p -> assertThat(p).containsExactly(1L, 11L));

        assertThat(BankReconciliationServiceImpl.pair(
                List.of(new StatementCandidate(1L, D, amt("-10000"), null)), books))
                .isEmpty();                                                                       // a tie is left for a person

        assertThat(BankReconciliationServiceImpl.pair(
                List.of(new StatementCandidate(1L, D, amt("-10000"), null)),
                List.of(books.get(0), new BookCandidate(12L, D.minusDays(6), amt("-10000"), "PAY/3"))))
                .singleElement().satisfies(p -> assertThat(p).containsExactly(1L, 10L));          // nearer date wins
    }

    @Test
    void pair_aBookEntryIsUsedOnce() {
        List<long[]> pairs = BankReconciliationServiceImpl.pair(
                List.of(new StatementCandidate(1L, D, amt("59"), null), new StatementCandidate(2L, D.plusDays(1), amt("59"), null)),
                List.of(new BookCandidate(10L, D, amt("59"), "")));
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0)[0]).isEqualTo(1L);
    }

    // ─── reconciliation statement ────────────────────────────────────────────

    /**
     * Books at 30 Apr: opening 1,00,000 + receipt 1,18,000 (bank credited 2 Apr) − cheque 25,000
     * (not presented) − cheque 5,000 (cleared 3 May, after the date) = 1,88,000.
     * Bank: 1,00,000 + 1,18,000 − charges 59 (not in books) = 2,17,941.
     * 1,88,000 + 25,000 + 5,000 − 59 = 2,17,941. Ties.
     */
    @Test
    void statement_explainsEveryDifference_andTies() {
        LocalDate asOf = LocalDate.of(2026, 4, 30);
        VoucherLine receipt = bookLine(LocalDate.of(2026, 4, 1), "118000", "0");
        VoucherLine chequeOut = bookLine(LocalDate.of(2026, 4, 20), "0", "25000");
        VoucherLine chequeLate = bookLine(LocalDate.of(2026, 4, 25), "0", "5000");
        when(voucherLineRepo.standingLinesUpTo(4011L, asOf)).thenReturn(List.of(receipt, chequeOut, chequeLate));
        when(voucherLineRepo.aggregateByAccount(asOf)).thenReturn(List.<Object[]>of(
                new Object[]{4011L, new BigDecimal("218000"), new BigDecimal("30000")}));   // incl. 1,00,000 opening

        BankStatementLine credit = stmt(LocalDate.of(2026, 4, 2), "0", "118000", "218000");
        credit.setVoucherLine(receipt);
        BankStatementLine charges = stmt(LocalDate.of(2026, 4, 5), "59", "0", "217941");
        BankStatementLine lateClear = stmt(LocalDate.of(2026, 5, 3), "5000", "0", "212941");
        lateClear.setVoucherLine(chequeLate);
        when(lineRepo.findLive(4011L)).thenReturn(List.of(credit, charges, lateClear));
        when(lineRepo.findLiveUpTo(4011L, asOf)).thenReturn(List.of(credit, charges));

        BankReconciliationStatementDto s = service.statement(4011L, asOf);

        assertThat(s.bookBalance()).isEqualByComparingTo("188000");
        assertThat(s.depositsNotCredited()).isEmpty();
        assertThat(s.paymentsNotDebitedTotal()).isEqualByComparingTo("30000");     // both cheques: one unmatched, one cleared after the date
        assertThat(s.bankDebitsNotInBooksTotal()).isEqualByComparingTo("59");
        assertThat(s.computedBankBalance()).isEqualByComparingTo("217941");
        assertThat(s.statementBalance()).isEqualByComparingTo("217941");
        assertThat(s.tiesOut()).isTrue();
    }

    @Test
    void statement_reconcileFrom_leavesTheOpeningEntryOut() {
        LocalDate asOf = LocalDate.of(2026, 4, 30);
        BankStatementMapping m = new BankStatementMapping();
        m.setReconcileFrom(LocalDate.of(2026, 4, 1));
        when(mappingRepo.findByLedgerAccountId(4011L)).thenReturn(Optional.of(m));
        VoucherLine opening = bookLine(LocalDate.of(2026, 3, 31), "100000", "0");
        when(voucherLineRepo.standingLinesUpTo(4011L, asOf)).thenReturn(List.of(opening));
        when(voucherLineRepo.aggregateByAccount(asOf)).thenReturn(List.<Object[]>of(
                new Object[]{4011L, new BigDecimal("100000"), BigDecimal.ZERO}));
        when(lineRepo.findLive(4011L)).thenReturn(List.of());
        when(lineRepo.findLiveUpTo(4011L, asOf)).thenReturn(List.of());

        BankReconciliationStatementDto s = service.statement(4011L, asOf);

        assertThat(s.depositsNotCredited()).isEmpty();
        assertThat(s.computedBankBalance()).isEqualByComparingTo("100000");
        assertThat(s.statementBalance()).isNull();
        assertThat(s.tiesOut()).isFalse();
    }

    // ─── manual matching ─────────────────────────────────────────────────────

    @Test
    void match_refusesDifferentAmounts_andReversedEntries() {
        BankStatementLine s = stmt(D, "500", "0", null);
        when(lineRepo.findByIdAndDeletedDateIsNull(s.getId())).thenReturn(Optional.of(s));
        VoucherLine wrongAmount = bookLine(D, "0", "50");
        when(voucherLineRepo.findById(wrongAmount.getId())).thenReturn(Optional.of(wrongAmount));

        assertThatThrownBy(() -> service.match(s.getId(), wrongAmount.getId(), "priya"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Amounts differ");

        VoucherLine reversed = bookLine(D, "0", "500");
        reversed.getVoucher().setStatus(VoucherStatus.REVERSED);
        when(voucherLineRepo.findById(reversed.getId())).thenReturn(Optional.of(reversed));
        assertThatThrownBy(() -> service.match(s.getId(), reversed.getId(), "priya"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reversed");
        verify(lineRepo, never()).save(any());
    }

    @Test
    void aMatchToAnEntryLaterReversed_countsAsUnmatched() {
        BankStatementLine s = stmt(D, "500", "0", null);
        VoucherLine vl = bookLine(D, "0", "500");
        s.setVoucherLine(vl);
        assertThat(BankReconciliationServiceImpl.isMatched(s)).isTrue();
        vl.getVoucher().setStatus(VoucherStatus.REVERSED);
        assertThat(BankReconciliationServiceImpl.isMatched(s)).isFalse();
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private static BigDecimal amt(String s) {
        return new BigDecimal(s).setScale(2);
    }

    private VoucherLine bookLine(LocalDate date, String dr, String cr) {
        Voucher v = new Voucher();
        v.setId(nextId++);
        v.setVoucherNumber("V/" + v.getId());
        v.setVoucherType(VoucherType.PAYMENT);
        v.setDate(date);
        v.setStatus(VoucherStatus.POSTED);
        v.setLines(new ArrayList<>());
        VoucherLine vl = new VoucherLine();
        vl.setId(nextId++);
        vl.setVoucher(v);
        vl.setLedgerAccount(bank);
        vl.setDrAmount(new BigDecimal(dr));
        vl.setCrAmount(new BigDecimal(cr));
        return vl;
    }

    private BankStatementLine stmt(LocalDate date, String withdrawal, String deposit, String balance) {
        BankStatementLine s = new BankStatementLine();
        s.setId(nextId++);
        s.setLedgerAccount(bank);
        s.setTxnDate(date);
        s.setWithdrawal(new BigDecimal(withdrawal));
        s.setDeposit(new BigDecimal(deposit));
        s.setBalance(balance != null ? new BigDecimal(balance) : null);
        s.setImportBatch("b");
        return s;
    }
}
