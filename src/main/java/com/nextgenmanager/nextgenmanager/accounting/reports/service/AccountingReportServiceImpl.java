package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountingReportServiceImpl implements AccountingReportService {

    private final LedgerAccountRepository ledgerRepo;
    private final VoucherLineRepository voucherLineRepo;
    private final VoucherRepository voucherRepo;

    @Override
    public TrialBalanceDto trialBalance(LocalDate asOf) {
        // Fetch all active ledger accounts with their group (JOIN FETCH — no N+1)
        List<LedgerAccount> accounts = ledgerRepo.findAllWithGroupOrderByCode();

        // Aggregate Dr/Cr per account from all POSTED vouchers up to asOf
        List<Object[]> rows = voucherLineRepo.aggregateByAccount(asOf);
        Map<Long, BigDecimal[]> movementMap = new HashMap<>();
        for (Object[] row : rows) {
            Long accountId = (Long) row[0];
            BigDecimal dr = (BigDecimal) row[1];
            BigDecimal cr = (BigDecimal) row[2];
            movementMap.put(accountId, new BigDecimal[]{dr, cr});
        }

        List<TrialBalanceLine> lines = new ArrayList<>();
        BigDecimal grandDr = BigDecimal.ZERO;
        BigDecimal grandCr = BigDecimal.ZERO;

        for (LedgerAccount la : accounts) {
            BigDecimal[] movement = movementMap.getOrDefault(la.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal movDr = movement[0];
            BigDecimal movCr = movement[1];

            // Opening balance (from LedgerAccount fields — zero for voucher-based openings)
            BigDecimal openDr = BigDecimal.ZERO;
            BigDecimal openCr = BigDecimal.ZERO;
            if (la.getOpeningBalance() != null && la.getOpeningBalance().compareTo(BigDecimal.ZERO) != 0) {
                if ("DR".equals(la.getOpeningBalanceDrCr())) openDr = la.getOpeningBalance();
                else openCr = la.getOpeningBalance();
            }

            BigDecimal closingDr = openDr.add(movDr);
            BigDecimal closingCr = openCr.add(movCr);

            // Net to one side
            BigDecimal net = closingDr.subtract(closingCr);
            BigDecimal finalDr = net.compareTo(BigDecimal.ZERO) >= 0 ? net : BigDecimal.ZERO;
            BigDecimal finalCr = net.compareTo(BigDecimal.ZERO) < 0  ? net.negate() : BigDecimal.ZERO;

            if (finalDr.compareTo(BigDecimal.ZERO) == 0 && finalCr.compareTo(BigDecimal.ZERO) == 0
                    && openDr.compareTo(BigDecimal.ZERO) == 0 && openCr.compareTo(BigDecimal.ZERO) == 0
                    && movDr.compareTo(BigDecimal.ZERO) == 0 && movCr.compareTo(BigDecimal.ZERO) == 0) {
                continue; // skip zero-balance zero-movement accounts
            }

            TrialBalanceLine line = new TrialBalanceLine();
            line.setAccountId(la.getId());
            line.setAccountCode(la.getCode());
            line.setAccountName(la.getName());
            line.setGroupName(la.getGroup().getName());
            line.setNature(la.getNature());
            line.setOpeningDr(openDr);
            line.setOpeningCr(openCr);
            line.setMovementDr(movDr);
            line.setMovementCr(movCr);
            line.setClosingDr(finalDr);
            line.setClosingCr(finalCr);
            lines.add(line);

            grandDr = grandDr.add(finalDr);
            grandCr = grandCr.add(finalCr);
        }

        TrialBalanceDto tb = new TrialBalanceDto();
        tb.setAsOf(asOf);
        tb.setLines(lines);
        tb.setTotalDr(grandDr.setScale(2, RoundingMode.HALF_UP));
        tb.setTotalCr(grandCr.setScale(2, RoundingMode.HALF_UP));
        tb.setBalanced(grandDr.setScale(2, RoundingMode.HALF_UP)
                              .compareTo(grandCr.setScale(2, RoundingMode.HALF_UP)) == 0);
        return tb;
    }

    @Override
    public DayBookDto dayBook(LocalDate from, LocalDate to) {
        // JOIN FETCH lines + ledgerAccount in one query — no N+1
        List<Voucher> vouchers = voucherRepo.findPostedWithLinesByDateRange(from, to);

        List<DayBookLineDto> dayLines = new ArrayList<>();
        BigDecimal grandDr = BigDecimal.ZERO;
        BigDecimal grandCr = BigDecimal.ZERO;

        for (Voucher v : vouchers) {
            // Load lines — safe because we're in @Transactional
            DayBookLineDto dl = new DayBookLineDto();
            dl.setVoucherId(v.getId());
            dl.setVoucherNumber(v.getVoucherNumber());
            dl.setVoucherType(v.getVoucherType());
            dl.setDate(v.getDate());
            dl.setNarration(v.getNarration());

            BigDecimal dr = BigDecimal.ZERO;
            BigDecimal cr = BigDecimal.ZERO;
            List<DayBookLineDto.VoucherLineEntryDto> lineEntries = new ArrayList<>();
            for (var vl : v.getLines()) {
                DayBookLineDto.VoucherLineEntryDto le = new DayBookLineDto.VoucherLineEntryDto();
                le.setAccountCode(vl.getLedgerAccount().getCode());
                le.setAccountName(vl.getLedgerAccount().getName());
                le.setDrAmount(vl.getDrAmount());
                le.setCrAmount(vl.getCrAmount());
                le.setLineNarration(vl.getNarration());
                dr = dr.add(vl.getDrAmount());
                cr = cr.add(vl.getCrAmount());
                lineEntries.add(le);
            }
            dl.setTotalDr(dr);
            dl.setTotalCr(cr);
            dl.setLines(lineEntries);
            dayLines.add(dl);
            grandDr = grandDr.add(dr);
            grandCr = grandCr.add(cr);
        }

        DayBookDto dto = new DayBookDto();
        dto.setFrom(from);
        dto.setTo(to);
        dto.setVouchers(dayLines);
        dto.setGrandTotalDr(grandDr);
        dto.setGrandTotalCr(grandCr);
        return dto;
    }

    @Override
    public LedgerStatementDto ledgerStatement(Long accountId, LocalDate from, LocalDate to) {
        LedgerAccount la = ledgerRepo.findByIdAndDeletedDateIsNull(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + accountId));

        // Opening balance = LedgerAccount base + all posted lines before 'from'
        List<Object[]> priorRows = voucherLineRepo.aggregateByAccount(from.minusDays(1));
        BigDecimal priorDr = BigDecimal.ZERO;
        BigDecimal priorCr = BigDecimal.ZERO;
        for (Object[] row : priorRows) {
            if (accountId.equals(row[0])) {
                priorDr = (BigDecimal) row[1];
                priorCr = (BigDecimal) row[2];
                break;
            }
        }
        BigDecimal openingNet = priorDr.subtract(priorCr);
        String openingDrCr = openingNet.compareTo(BigDecimal.ZERO) >= 0 ? "DR" : "CR";
        BigDecimal openingBalance = openingNet.abs();

        // Lines in range
        List<Object[]> lineRows = voucherLineRepo.ledgerStatement(accountId, from, to);
        List<LedgerLineDto> lines = new ArrayList<>();
        BigDecimal running = openingNet;

        for (Object[] row : lineRows) {
            LedgerLineDto line = new LedgerLineDto();
            line.setDate((LocalDate) row[0]);
            line.setVoucherNumber((String) row[1]);
            line.setVoucherType((VoucherType) row[2]);
            line.setNarration((String) row[3]);
            line.setDrAmount((BigDecimal) row[4]);
            line.setCrAmount((BigDecimal) row[5]);
            running = running.add(line.getDrAmount()).subtract(line.getCrAmount());
            line.setRunningBalance(running.abs());
            line.setRunningBalanceDrCr(running.compareTo(BigDecimal.ZERO) >= 0 ? "DR" : "CR");
            lines.add(line);
        }

        LedgerStatementDto dto = new LedgerStatementDto();
        dto.setAccountId(accountId);
        dto.setAccountCode(la.getCode());
        dto.setAccountName(la.getName());
        dto.setFrom(from);
        dto.setTo(to);
        dto.setOpeningBalance(openingBalance);
        dto.setOpeningBalanceDrCr(openingDrCr);
        dto.setLines(lines);
        dto.setClosingBalance(running.abs());
        dto.setClosingBalanceDrCr(running.compareTo(BigDecimal.ZERO) >= 0 ? "DR" : "CR");
        return dto;
    }
}
