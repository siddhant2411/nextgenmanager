package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsChallanDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.events.TdsChallanDepositedEvent;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsChallan;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntryStatus;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsChallanRepository;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsEntryRepository;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TdsChallanServiceImpl implements TdsChallanService {

    private final TdsChallanRepository challanRepo;
    private final TdsEntryRepository entryRepo;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public TdsChallanDto createChallan(TdsChallanCreateDto dto, String username) {
        List<TdsEntry> pending = entryRepo.findByFinancialYearAndQuarterAndStatusAndDeletedDateIsNull(
                dto.getFinancialYear(), dto.getQuarter(), TdsEntryStatus.DEDUCTED);
        if (dto.getSection() != null && !dto.getSection().isBlank()) {
            pending = pending.stream()
                    .filter(e -> e.getSection() != null && dto.getSection().equals(e.getSection().getSection()))
                    .toList();
        }
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("No pending TDS deductions for "
                    + dto.getFinancialYear() + " " + dto.getQuarter()
                    + (dto.getSection() != null ? " section " + dto.getSection() : ""));
        }

        BigDecimal total = pending.stream()
                .map(TdsEntry::getTdsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        TdsChallan challan = new TdsChallan();
        challan.setChallanNumber(dto.getChallanNumber());
        challan.setBsrCode(dto.getBsrCode());
        challan.setDepositDate(dto.getDepositDate());
        challan.setAmount(total);
        challan.setSection(dto.getSection());
        challan.setFinancialYear(dto.getFinancialYear());
        challan.setQuarter(dto.getQuarter());
        challan.setNotes(dto.getNotes());
        challan.setCreatedBy(username);
        TdsChallan saved = challanRepo.save(challan);

        for (TdsEntry e : pending) {
            e.setChallanId(saved.getId());
            e.setStatus(TdsEntryStatus.DEPOSITED);
        }
        entryRepo.saveAll(pending);

        // Accounting clears TDS Payable against the bank (listener runs after commit).
        domainEventPublisher.publish(new TdsChallanDepositedEvent(saved.getId()));

        return toDto(saved, pending.size(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsChallanDto> listChallans(String financialYear) {
        List<TdsChallan> challans = (financialYear != null && !financialYear.isBlank())
                ? challanRepo.findByFinancialYearAndDeletedDateIsNullOrderByDepositDateDesc(financialYear)
                : challanRepo.findByDeletedDateIsNullOrderByDepositDateDesc();
        return challans.stream()
                .map(c -> toDto(c, entryRepo.findByChallanIdAndDeletedDateIsNull(c.getId()).size(), false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TdsChallanDto getChallan(Long id) {
        TdsChallan c = challanRepo.findById(id)
                .filter(x -> x.getDeletedDate() == null)
                .orElseThrow(() -> new IllegalArgumentException("TDS challan not found: " + id));
        List<TdsEntry> entries = entryRepo.findByChallanIdAndDeletedDateIsNull(id);
        TdsChallanDto dto = toDto(c, entries.size(), true);
        dto.setEntries(entries.stream().map(TdsMapper::toEntryDto).toList());
        return dto;
    }

    private TdsChallanDto toDto(TdsChallan c, int entryCount, boolean detail) {
        TdsChallanDto dto = new TdsChallanDto();
        dto.setId(c.getId());
        dto.setChallanNumber(c.getChallanNumber());
        dto.setBsrCode(c.getBsrCode());
        dto.setDepositDate(c.getDepositDate());
        dto.setAmount(c.getAmount());
        dto.setSection(c.getSection());
        dto.setFinancialYear(c.getFinancialYear());
        dto.setQuarter(c.getQuarter());
        dto.setNotes(c.getNotes());
        dto.setEntryCount(entryCount);
        return dto;
    }
}
