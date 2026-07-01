package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsSectionCreateDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsSectionDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsApplicableTo;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsSection;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TdsSectionServiceImpl implements TdsSectionService {

    private final TdsSectionRepository sectionRepo;

    @Override
    @Transactional(readOnly = true)
    public List<TdsSectionDto> listAll() {
        return sectionRepo.findByDeletedDateIsNullOrderBySectionAsc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsSectionDto> listActive() {
        return sectionRepo.findByActiveTrueAndDeletedDateIsNullOrderBySectionAsc().stream().map(this::toDto).toList();
    }

    @Override
    public TdsSectionDto create(TdsSectionCreateDto dto) {
        sectionRepo.findBySectionAndDeletedDateIsNull(dto.getSection()).ifPresent(s -> {
            throw new IllegalArgumentException("TDS section already exists: " + dto.getSection());
        });
        TdsSection s = new TdsSection();
        apply(s, dto);
        return toDto(sectionRepo.save(s));
    }

    @Override
    public TdsSectionDto update(Long id, TdsSectionCreateDto dto) {
        TdsSection s = sectionRepo.findById(id)
                .filter(x -> x.getDeletedDate() == null)
                .orElseThrow(() -> new IllegalArgumentException("TDS section not found: " + id));
        apply(s, dto);
        return toDto(sectionRepo.save(s));
    }

    @Override
    public void delete(Long id) {
        TdsSection s = sectionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TDS section not found: " + id));
        s.setDeletedDate(new Date());
        sectionRepo.save(s);
    }

    private void apply(TdsSection s, TdsSectionCreateDto dto) {
        s.setSection(dto.getSection());
        s.setDescription(dto.getDescription());
        s.setRate(dto.getRate());
        s.setPanMissingRate(dto.getPanMissingRate() != null ? dto.getPanMissingRate() : new BigDecimal("20"));
        s.setThresholdSingle(dto.getThresholdSingle());
        s.setThresholdAnnual(dto.getThresholdAnnual());
        s.setApplicableTo(dto.getApplicableTo() != null ? dto.getApplicableTo() : TdsApplicableTo.VENDOR_PAYMENT);
        s.setActive(dto.getActive() == null || dto.getActive());
    }

    private TdsSectionDto toDto(TdsSection s) {
        TdsSectionDto dto = new TdsSectionDto();
        dto.setId(s.getId());
        dto.setSection(s.getSection());
        dto.setDescription(s.getDescription());
        dto.setRate(s.getRate());
        dto.setPanMissingRate(s.getPanMissingRate());
        dto.setThresholdSingle(s.getThresholdSingle());
        dto.setThresholdAnnual(s.getThresholdAnnual());
        dto.setApplicableTo(s.getApplicableTo());
        dto.setActive(s.isActive());
        return dto;
    }
}
