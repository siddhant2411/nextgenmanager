package com.nextgenmanager.nextgenmanager.accounting.opening.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.opening.dto.OpeningBalanceRowDto;
import com.nextgenmanager.nextgenmanager.accounting.opening.model.OpeningBalance;
import com.nextgenmanager.nextgenmanager.accounting.opening.repository.OpeningBalanceRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.FinancialYearRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class OpeningBalanceServiceImpl implements OpeningBalanceService {

    private final LedgerAccountRepository ledgerRepo;
    private final OpeningBalanceRepository openingBalanceRepo;
    private final FinancialYearRepository fyRepo;
    private final ContactRepository contactRepo;
    private final PostingService postingService;

    @Override
    public VoucherDto importFromExcel(MultipartFile file, LocalDate openingDate, String username) {
        if (openingBalanceRepo.existsByOpeningDateAndDeletedDateIsNull(openingDate)) {
            throw new IllegalStateException("Opening balances have already been imported for date: " + openingDate);
        }

        List<OpeningBalanceRowDto> rows = parseExcel(file);

        // Validate that rows balance (Σ DR = Σ CR)
        BigDecimal totalDr = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;
        for (OpeningBalanceRowDto row : rows) {
            if ("DR".equalsIgnoreCase(row.getDrCr())) totalDr = totalDr.add(row.getAmount());
            else totalCr = totalCr.add(row.getAmount());
        }
        if (totalDr.compareTo(totalCr) != 0) {
            throw new IllegalArgumentException(
                    "Opening balances do not balance: Dr=" + totalDr + " Cr=" + totalCr);
        }

        // Persist OpeningBalance rows, then collapse them to ONE voucher line per ledger.
        //
        // A party can carry many open bills (V162), and each is its own OpeningBalance row so that
        // ageing can bucket it by its real date. The GL must not see them individually: one line
        // per ledger keeps the OPENING voucher readable and keeps the sub-ledger tying to its
        // control account by construction.
        Map<Long, BigDecimal> netByLedger = new LinkedHashMap<>();   // + = net debit
        for (OpeningBalanceRowDto row : rows) {
            LedgerAccount la = ledgerRepo.findByCodeAndDeletedDateIsNull(row.getLedgerCode())
                    .orElseThrow(() -> new IllegalArgumentException("Ledger not found: " + row.getLedgerCode()));

            OpeningBalance ob = new OpeningBalance();
            ob.setLedgerAccount(la);
            ob.setAmount(row.getAmount());
            ob.setDrCr(row.getDrCr().toUpperCase());
            ob.setOpeningDate(openingDate);
            ob.setBillReference(row.getBillReference());
            ob.setBillDate(row.getBillDate());
            ob.setDueDate(row.getDueDate());
            if (row.getContactCode() != null && !row.getContactCode().isBlank()) {
                contactRepo.findByContactCodeAndDeletedDateIsNull(row.getContactCode())
                        .ifPresent(ob::setContact);
            }
            openingBalanceRepo.save(ob);

            BigDecimal signed = "DR".equalsIgnoreCase(row.getDrCr())
                    ? row.getAmount() : row.getAmount().negate();
            netByLedger.merge(la.getId(), signed, BigDecimal::add);
        }

        List<VoucherLineDraft> lineDrafts = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : netByLedger.entrySet()) {
            BigDecimal net = e.getValue();
            if (net.signum() == 0) continue;      // bills that cancel out contribute no GL line
            VoucherLineDraft ld = new VoucherLineDraft();
            ld.setLedgerAccountId(e.getKey());
            if (net.signum() > 0) ld.setDrAmount(net);
            else                  ld.setCrAmount(net.negate());
            lineDrafts.add(ld);
        }

        // Post as a single OPENING voucher
        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.OPENING);
        draft.setDate(openingDate);
        draft.setNarration("Opening balances as of " + openingDate);
        draft.setLines(lineDrafts);

        VoucherDto voucher = postingService.post(draft, username);

        // Link voucher to financial year
        Long voucherId = voucher.getId();
        if (voucherId != null) {
            fyRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(openingDate, openingDate)
                    .ifPresent(fy -> {
                        fy.setOpeningDate(openingDate);
                        fy.setOpeningEntryVoucherId(voucherId);
                        fyRepo.save(fy);
                    });
        }

        return voucher;
    }

    @Override
    public void setOpeningDate(Long financialYearId, LocalDate openingDate) {
        FinancialYear fy = fyRepo.findById(financialYearId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial year not found: " + financialYearId));
        fy.setOpeningDate(openingDate);
        fyRepo.save(fy);
    }

    // ─── Excel parser ─────────────────────────────────────────────────────────
    // A=ledgerCode, B=contactCode(optional), C=amount, D=drCr,
    // E=billReference, F=billDate, G=dueDate  (E-G optional, added V162)
    //
    // One row per OPEN BILL for a party, or a single row carrying the whole ledger balance.
    // Files written before V162 have only A-D and still parse unchanged.

    private List<OpeningBalanceRowDto> parseExcel(MultipartFile file) {
        List<OpeningBalanceRowDto> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {  // skip header row 0
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String ledgerCode = getCellString(row, 0);
                if (ledgerCode == null || ledgerCode.isBlank()) continue;

                OpeningBalanceRowDto dto = new OpeningBalanceRowDto();
                dto.setLedgerCode(ledgerCode.trim());
                dto.setContactCode(getCellString(row, 1));
                Cell amtCell = row.getCell(2);
                dto.setAmount(amtCell == null ? BigDecimal.ZERO
                        : BigDecimal.valueOf(amtCell.getNumericCellValue()));
                dto.setDrCr(getCellString(row, 3));
                dto.setBillReference(trimToNull(getCellString(row, 4)));
                dto.setBillDate(getCellDate(row, 5));
                dto.setDueDate(getCellDate(row, 6));
                rows.add(dto);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse opening balance Excel: " + e.getMessage(), e);
        }
        return rows;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> null;
        };
    }

    /** Accepts a real Excel date cell or an ISO yyyy-MM-dd string; anything else is treated as absent. */
    private LocalDate getCellDate(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate() : null;
            }
            if (cell.getCellType() == CellType.STRING) {
                String v = cell.getStringCellValue().trim();
                return v.isEmpty() ? null : LocalDate.parse(v);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Row " + (row.getRowNum() + 1) + ": unreadable date in column "
                            + (char) ('A' + col) + " — use a date cell or yyyy-MM-dd");
        }
        return null;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
