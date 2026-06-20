package com.nextgenmanager.nextgenmanager.accounting.voucher.service;

import com.nextgenmanager.nextgenmanager.accounting.posting.LedgerResolver;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.DepreciationVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.PayrollVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.cr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.dr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.nz;

@Service
@RequiredArgsConstructor
public class StatutoryVoucherServiceImpl implements StatutoryVoucherService {

    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @Override
    public VoucherDto postPayroll(PayrollVoucherRequest req, String username) {
        BigDecimal gross = nz(req.getGrossSalary());
        BigDecimal eePf = nz(req.getEmployeePf());
        BigDecimal erPf = nz(req.getEmployerPf());
        BigDecimal eeEsi = nz(req.getEmployeeEsi());
        BigDecimal erEsi = nz(req.getEmployerEsi());
        BigDecimal pt = nz(req.getProfessionalTax());
        BigDecimal tds = nz(req.getTds());

        BigDecimal netPay = gross.subtract(eePf).subtract(eeEsi).subtract(pt).subtract(tds);
        if (netPay.signum() < 0) {
            throw new InvalidVoucherException("Payroll deductions exceed gross salary — net pay is negative");
        }

        // Dr Salaries & Wages = gross + employer contributions (the full employer cost).
        BigDecimal salaryCost = gross.add(erPf).add(erEsi);

        List<VoucherLineDraft> lines = new ArrayList<>();
        lines.add(dr(ledgers.salariesAndWages().getId(), salaryCost, "Salaries & wages", null));
        addCrIfPositive(lines, ledgers.pfPayable().getId(), eePf.add(erPf), "PF payable");
        addCrIfPositive(lines, ledgers.esiPayable().getId(), eeEsi.add(erEsi), "ESI payable");
        addCrIfPositive(lines, ledgers.professionalTaxPayable().getId(), pt, "Professional tax payable");
        addCrIfPositive(lines, ledgers.tdsPayable().getId(), tds, "TDS on salary payable");
        addCrIfPositive(lines, ledgers.salaryPayable().getId(), netPay, "Net salary payable");

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.PAYROLL);
        draft.setDate(req.getDate());
        draft.setNarration(req.getNarration() != null ? req.getNarration() : "Payroll");
        draft.setSourceDocType(SourceDocTypes.PAYROLL); // bypasses the manual-voucher approval gate
        draft.setLines(lines);

        return postingService.post(draft, username);
    }

    @Override
    public VoucherDto postDepreciation(DepreciationVoucherRequest req, String username) {
        BigDecimal amount = nz(req.getAmount());

        List<VoucherLineDraft> lines = List.of(
                dr(ledgers.depreciationExpense().getId(), amount, "Depreciation for the period", null),
                cr(ledgers.accumulatedDepreciation().getId(), amount, "Accumulated depreciation", null)
        );

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.DEPRECIATION);
        draft.setDate(req.getDate());
        draft.setNarration(req.getNarration() != null ? req.getNarration() : "Depreciation");
        draft.setSourceDocType(SourceDocTypes.DEPRECIATION);
        draft.setLines(lines);

        return postingService.post(draft, username);
    }

    private void addCrIfPositive(List<VoucherLineDraft> lines, Long ledgerId, BigDecimal amount, String narration) {
        if (amount.signum() > 0) {
            lines.add(cr(ledgerId, amount, narration, null));
        }
    }
}
