package com.nextgenmanager.nextgenmanager.accounting.gst.filing.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.filing.dto.GstReturnDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturn;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturnStatus;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturnType;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.repository.GstReturnRepository;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto.Gstr1Dto;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service.Gstr1Service;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.Gstr3bDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.TaxAmounts;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.service.Gstr3bService;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.AccountingPeriodRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GstReturnServiceImpl implements GstReturnService {

    private static final DateTimeFormatter PERIOD_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final GstReturnRepository returnRepo;
    private final AccountingPeriodRepository periodRepo;
    private final FinancialYearService financialYearService;
    private final Gstr1Service gstr1Service;
    private final Gstr3bService gstr3bService;

    // ── GSTR-1: record, no hard lock ─────────────────────────────────────────────

    @Override
    public GstReturnDto fileGstr1(Long periodId, String user) {
        AccountingPeriod period = period(periodId);
        Gstr1Dto dto = gstr1Service.build(period.getStartDate(), period.getEndDate());
        Gstr1Dto.Gstr1Totals t = dto.totals();

        GstReturn ret = newSnapshot(period, GstReturnType.GSTR1, user,
                t.taxableValue(), t.cgst(), t.sgst(), t.igst(), t.cess(),
                new String(gstr1Service.toJson(dto), StandardCharsets.UTF_8));
        return toDto(returnRepo.save(ret), false);
    }

    // ── GSTR-3B: file then lock the period ───────────────────────────────────────

    @Override
    public GstReturnDto fileGstr3b(Long periodId, String user) {
        AccountingPeriod period = period(periodId);
        if (period.getStatus() == PeriodStatus.LOCKED || period.getStatus() == PeriodStatus.CLOSED) {
            throw new IllegalStateException("Period " + label(period) + " is " + period.getStatus()
                    + ". Unlock it before re-filing GSTR-3B.");
        }
        Gstr3bDto dto = gstr3bService.build(period.getStartDate(), period.getEndDate());
        TaxAmounts o = dto.outwardTaxable();

        GstReturn ret = newSnapshot(period, GstReturnType.GSTR3B, user,
                o.taxableValue(), o.cgst(), o.sgst(), o.igst(), o.cess(),
                new String(gstr3bService.toJson(dto), StandardCharsets.UTF_8));
        GstReturnDto saved = toDto(returnRepo.save(ret), false);

        financialYearService.lockPeriod(periodId);   // filing 3B locks the month
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GstReturnDto> getFilings(Long financialYearId) {
        return returnRepo.findFiledByFinancialYear(financialYearId).stream()
                .map(r -> toDto(r, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GstReturnDto getReturn(Long id) {
        return returnRepo.findByIdAndDeletedDateIsNull(id)
                .map(r -> toDto(r, true))
                .orElseThrow(() -> new IllegalArgumentException("GST return " + id + " not found"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private AccountingPeriod period(Long periodId) {
        return periodRepo.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period " + periodId + " not found"));
    }

    /** Builds a fresh FILED snapshot, soft-deleting any prior live return for the same period+type. */
    private GstReturn newSnapshot(AccountingPeriod period, GstReturnType type, String user,
                                  BigDecimal taxable, BigDecimal cgst, BigDecimal sgst,
                                  BigDecimal igst, BigDecimal cess, String payload) {
        returnRepo.findByPeriodIdAndReturnTypeAndDeletedDateIsNull(period.getId(), type)
                .ifPresent(prior -> { prior.setDeletedDate(new Date()); returnRepo.save(prior); });

        GstReturn ret = new GstReturn();
        ret.setFinancialYear(period.getFinancialYear());
        ret.setPeriod(period);
        ret.setReturnType(type);
        ret.setStatus(GstReturnStatus.FILED);
        ret.setPeriodLabel(label(period));
        ret.setTaxableValue(nz(taxable));
        ret.setCgst(nz(cgst));
        ret.setSgst(nz(sgst));
        ret.setIgst(nz(igst));
        ret.setCess(nz(cess));
        ret.setTotal(nz(cgst).add(nz(sgst)).add(nz(igst)).add(nz(cess)));
        ret.setPayload(payload);
        ret.setFiledBy(user);
        ret.setFiledDate(new Date());
        return ret;
    }

    private String label(AccountingPeriod period) {
        return period.getStartDate() != null ? period.getStartDate().format(PERIOD_LABEL) : null;
    }

    private GstReturnDto toDto(GstReturn r, boolean includePayload) {
        return new GstReturnDto(
                r.getId(), r.getReturnType().name(), r.getStatus().name(), r.getPeriodLabel(),
                r.getPeriod() != null ? r.getPeriod().getId() : null,
                r.getFinancialYear() != null ? r.getFinancialYear().getId() : null,
                r.getTaxableValue(), r.getCgst(), r.getSgst(), r.getIgst(), r.getCess(), r.getTotal(),
                r.getFiledBy(), r.getFiledDate(), includePayload ? r.getPayload() : null);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
