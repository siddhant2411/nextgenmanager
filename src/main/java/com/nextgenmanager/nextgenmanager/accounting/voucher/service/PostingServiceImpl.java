package com.nextgenmanager.nextgenmanager.accounting.voucher.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.UnbalancedVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.*;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.approval.dto.RequiredStep;
import com.nextgenmanager.nextgenmanager.common.approval.service.ApprovalEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class PostingServiceImpl implements PostingService {

    private final VoucherRepository voucherRepo;
    private final LedgerAccountRepository ledgerRepo;
    private final FinancialYearService fyService;
    private final VoucherNumberGenerator numberGenerator;
    private final ApprovalEngine approvalEngine;

    @Override
    public VoucherDto post(VoucherDraft draft, String username) {
        // 1. Structural validation
        validateDraft(draft);

        // 2. Resolve period (throws LockedPeriodException if not OPEN)
        AccountingPeriod period = fyService.resolveOpenPeriod(draft.getDate());
        FinancialYear fy = period.getFinancialYear();

        // 3. Idempotency guard for auto-posted documents
        if (draft.getSourceDocType() != null && draft.getSourceDocId() != null) {
            if (voucherRepo.existsBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(
                    draft.getSourceDocType(), draft.getSourceDocId())) {
                throw new InvalidVoucherException("A voucher already exists for " +
                        draft.getSourceDocType() + "#" + draft.getSourceDocId());
            }
        }

        // 4. Build entity
        Voucher voucher = new Voucher();
        voucher.setVoucherType(draft.getVoucherType());
        voucher.setVoucherNumber(numberGenerator.next(draft.getVoucherType()));
        voucher.setDate(draft.getDate());
        voucher.setNarration(draft.getNarration());
        voucher.setSourceDocType(draft.getSourceDocType());
        voucher.setSourceDocId(draft.getSourceDocId());
        voucher.setFinancialYear(fy);
        voucher.setPeriod(period);
        voucher.setCreatedBy(username);

        List<VoucherLine> lines = buildLines(voucher, draft.getLines());
        voucher.setLines(lines);

        // 5. Determine status. The MANUAL_VOUCHER approval gate applies only to human-entered
        //    vouchers. Source-document auto-posts (sourceDocType set) bypass it — the originating
        //    document has its own approval, and routing GL postings through it would strand them
        //    in PENDING_APPROVAL and out of the Trial Balance the moment a policy is configured.
        boolean isManual = draft.getSourceDocType() == null;
        if (isManual) {
            Map<String, Object> context = new HashMap<>();
            context.put("amount", lines.stream().map(VoucherLine::getDrAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            context.put("voucherType", draft.getVoucherType().name());

            List<RequiredStep> steps = approvalEngine.evaluate("MANUAL_VOUCHER", context);
            if (!steps.isEmpty()) {
                voucher.setStatus(VoucherStatus.PENDING_APPROVAL);
                // Create the approval request (sourcing the voucher id after save)
                Voucher saved = voucherRepo.save(voucher);
                approvalEngine.submit("MANUAL_VOUCHER", saved.getId(), context, username);
                return toDto(saved);
            }
        }

        voucher.setStatus(VoucherStatus.POSTED);
        return toDto(voucherRepo.save(voucher));
    }

    @Override
    public VoucherDto reverse(Long voucherId, String reason, String username) {
        Voucher original = voucherRepo.findByIdWithLines(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + voucherId));

        if (original.getStatus() != VoucherStatus.POSTED) {
            throw new InvalidVoucherException("Only POSTED vouchers can be reversed. Status: " + original.getStatus());
        }
        if (original.getReversedByVoucher() != null) {
            throw new InvalidVoucherException("Voucher " + original.getVoucherNumber() + " has already been reversed.");
        }

        AccountingPeriod period = fyService.resolveOpenPeriod(original.getDate());
        FinancialYear fy = period.getFinancialYear();

        // Build reversal — swap Dr/Cr on each line
        Voucher reversal = new Voucher();
        reversal.setVoucherType(original.getVoucherType());
        reversal.setVoucherNumber(numberGenerator.next(original.getVoucherType()));
        reversal.setDate(original.getDate());
        reversal.setNarration("REVERSAL of " + original.getVoucherNumber() + ": " + reason);
        reversal.setFinancialYear(fy);
        reversal.setPeriod(period);
        reversal.setCreatedBy(username);
        reversal.setStatus(VoucherStatus.POSTED);
        reversal.setReversalOf(original);

        List<VoucherLine> reversalLines = new ArrayList<>();
        for (VoucherLine orig : original.getLines()) {
            VoucherLine rev = new VoucherLine();
            rev.setVoucher(reversal);
            rev.setLineNumber(orig.getLineNumber());
            rev.setLedgerAccount(orig.getLedgerAccount());
            rev.setDrAmount(orig.getCrAmount());  // swap
            rev.setCrAmount(orig.getDrAmount());  // swap
            rev.setNarration(orig.getNarration());
            rev.setTaxType(orig.getTaxType());
            rev.setHsnSac(orig.getHsnSac());
            rev.setTaxableValue(orig.getTaxableValue());
            rev.setTaxRate(orig.getTaxRate());
            reversalLines.add(rev);
        }
        reversal.setLines(reversalLines);
        Voucher savedReversal = voucherRepo.save(reversal);

        // Mark original as reversed
        original.setStatus(VoucherStatus.REVERSED);
        original.setReversedByVoucher(savedReversal);
        voucherRepo.save(original);

        return toDto(savedReversal);
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    private void validateDraft(VoucherDraft draft) {
        if (draft.getLines() == null || draft.getLines().size() < 2) {
            throw new InvalidVoucherException("A voucher must have at least 2 lines.");
        }

        BigDecimal totalDr = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;

        for (VoucherLineDraft line : draft.getLines()) {
            BigDecimal dr = line.getDrAmount() != null ? line.getDrAmount() : BigDecimal.ZERO;
            BigDecimal cr = line.getCrAmount() != null ? line.getCrAmount() : BigDecimal.ZERO;

            if (dr.compareTo(BigDecimal.ZERO) < 0 || cr.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidVoucherException("Line amounts cannot be negative.");
            }
            if (dr.compareTo(BigDecimal.ZERO) > 0 && cr.compareTo(BigDecimal.ZERO) > 0) {
                throw new InvalidVoucherException("A line cannot have both Dr and Cr amounts.");
            }
            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) {
                throw new InvalidVoucherException("A line must have either Dr or Cr amount > 0.");
            }

            totalDr = totalDr.add(dr);
            totalCr = totalCr.add(cr);
        }

        if (totalDr.setScale(2, RoundingMode.HALF_UP)
                   .compareTo(totalCr.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new UnbalancedVoucherException(totalDr.toPlainString(), totalCr.toPlainString());
        }
    }

    private List<VoucherLine> buildLines(Voucher voucher, List<VoucherLineDraft> drafts) {
        List<VoucherLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (VoucherLineDraft d : drafts) {
            LedgerAccount la = ledgerRepo.findByIdAndDeletedDateIsNull(d.getLedgerAccountId())
                    .orElseThrow(() -> new InvalidVoucherException("Ledger account not found: " + d.getLedgerAccountId()));
            VoucherLine line = new VoucherLine();
            line.setVoucher(voucher);
            line.setLineNumber(lineNo++);
            line.setLedgerAccount(la);
            line.setDrAmount(d.getDrAmount() != null ? d.getDrAmount() : BigDecimal.ZERO);
            line.setCrAmount(d.getCrAmount() != null ? d.getCrAmount() : BigDecimal.ZERO);
            line.setNarration(d.getNarration());
            line.setTaxType(d.getTaxType());
            line.setHsnSac(d.getHsnSac());
            line.setTaxableValue(d.getTaxableValue());
            line.setTaxRate(d.getTaxRate());
            lines.add(line);
        }
        return lines;
    }

    // ─── DTO conversion (called inside @Transactional — lazy safe) ───────────

    VoucherDto toDto(Voucher v) {
        VoucherDto dto = new VoucherDto();
        dto.setId(v.getId());
        dto.setVoucherNumber(v.getVoucherNumber());
        dto.setVoucherType(v.getVoucherType());
        dto.setDate(v.getDate());
        dto.setNarration(v.getNarration());
        dto.setStatus(v.getStatus());
        dto.setCreatedBy(v.getCreatedBy());
        dto.setSourceDocType(v.getSourceDocType());
        dto.setSourceDocId(v.getSourceDocId());
        if (v.getPeriod() != null) dto.setPeriodId(v.getPeriod().getId());
        if (v.getReversalOf() != null) dto.setReversalOfId(v.getReversalOf().getId());
        if (v.getReversedByVoucher() != null) dto.setReversedByVoucherId(v.getReversedByVoucher().getId());

        BigDecimal totalDr = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;
        List<VoucherLineDto> lineDtos = new ArrayList<>();
        for (VoucherLine vl : v.getLines()) {
            VoucherLineDto ld = new VoucherLineDto();
            ld.setId(vl.getId());
            ld.setDrAmount(vl.getDrAmount());
            ld.setCrAmount(vl.getCrAmount());
            ld.setNarration(vl.getNarration());
            ld.setTaxType(vl.getTaxType());
            ld.setHsnSac(vl.getHsnSac());
            ld.setTaxableValue(vl.getTaxableValue());
            ld.setTaxRate(vl.getTaxRate());
            // Access lazy LedgerAccount within this transaction
            LedgerAccount la = vl.getLedgerAccount();
            ld.setLedgerAccountId(la.getId());
            ld.setLedgerAccountCode(la.getCode());
            ld.setLedgerAccountName(la.getName());
            totalDr = totalDr.add(vl.getDrAmount());
            totalCr = totalCr.add(vl.getCrAmount());
            lineDtos.add(ld);
        }
        dto.setLines(lineDtos);
        dto.setTotalDr(totalDr);
        dto.setTotalCr(totalCr);
        return dto;
    }
}
