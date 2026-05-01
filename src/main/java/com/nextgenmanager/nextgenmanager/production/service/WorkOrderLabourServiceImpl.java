package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLabourEntryDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLabourEntryRequestDTO;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderLabourEntryMapper;
import com.nextgenmanager.nextgenmanager.production.model.LaborRole;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLabourEntry;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.LaborRoleRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderLabourEntryRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

@Service
public class WorkOrderLabourServiceImpl implements WorkOrderLabourService {

    @Autowired
    private WorkOrderLabourEntryRepository labourEntryRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private LaborRoleRepository laborRoleRepository;

    @Autowired
    private WorkOrderLabourEntryMapper labourEntryMapper;

    @Override
    @Transactional
    public WorkOrderLabourEntryDTO logLabour(Long operationId, WorkOrderLabourEntryRequestDTO request) {
        WorkOrderOperation operation = workOrderOperationRepository.findById(operationId)
                .orElseThrow(() -> new EntityNotFoundException("Operation not found: " + operationId));

        WorkOrderLabourEntry entry = new WorkOrderLabourEntry();
        entry.setWorkOrderOperation(operation);
        applyRequest(entry, request);

        return labourEntryMapper.toDTO(labourEntryRepository.save(entry));
    }

    @Override
    public List<WorkOrderLabourEntryDTO> getEntriesForOperation(Long operationId) {
        return labourEntryRepository.findByWorkOrderOperationIdAndDeletedDateIsNull(operationId)
                .stream().map(labourEntryMapper::toDTO).toList();
    }

    @Override
    public List<WorkOrderLabourEntryDTO> getEntriesForWorkOrder(Long workOrderId) {
        return labourEntryRepository.findByWorkOrderIdAndDeletedDateIsNull(workOrderId)
                .stream().map(labourEntryMapper::toDTO).toList();
    }

    @Override
    @Transactional
    public WorkOrderLabourEntryDTO updateEntry(Long entryId, WorkOrderLabourEntryRequestDTO request) {
        WorkOrderLabourEntry entry = labourEntryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Labour entry not found: " + entryId));
        if (entry.getDeletedDate() != null) {
            throw new IllegalStateException("Labour entry " + entryId + " has been deleted");
        }
        applyRequest(entry, request);
        return labourEntryMapper.toDTO(labourEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public void deleteEntry(Long entryId) {
        WorkOrderLabourEntry entry = labourEntryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Labour entry not found: " + entryId));
        entry.setDeletedDate(new Date());
        labourEntryRepository.save(entry);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void applyRequest(WorkOrderLabourEntry entry, WorkOrderLabourEntryRequestDTO req) {
        entry.setOperatorName(req.getOperatorName());
        entry.setLaborType(req.getLaborType() != null ? req.getLaborType() : entry.getLaborType());
        entry.setStartTime(req.getStartTime());
        entry.setEndTime(req.getEndTime());
        entry.setRemarks(req.getRemarks());

        LaborRole role = null;
        if (req.getLaborRoleId() != null) {
            role = laborRoleRepository.findById(req.getLaborRoleId())
                    .orElseThrow(() -> new EntityNotFoundException("LaborRole not found: " + req.getLaborRoleId()));
        }
        entry.setLaborRole(role);

        BigDecimal duration = resolveDuration(req);
        entry.setDurationMinutes(duration);

        BigDecimal rate = resolveRate(req, role);
        entry.setCostRatePerHour(rate);

        if (duration != null && rate != null) {
            entry.setTotalCost(duration.divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP).multiply(rate).setScale(2, RoundingMode.HALF_UP));
        } else {
            entry.setTotalCost(null);
        }
    }

    private BigDecimal resolveDuration(WorkOrderLabourEntryRequestDTO req) {
        if (req.getDurationMinutes() != null) {
            return req.getDurationMinutes();
        }
        if (req.getStartTime() != null && req.getEndTime() != null) {
            long millis = req.getEndTime().getTime() - req.getStartTime().getTime();
            if (millis > 0) {
                return BigDecimal.valueOf(millis / 60_000.0).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    private BigDecimal resolveRate(WorkOrderLabourEntryRequestDTO req, LaborRole role) {
        if (req.getCostRatePerHour() != null) {
            return req.getCostRatePerHour();
        }
        if (role != null && role.getCostPerHour() != null) {
            return role.getCostPerHour();
        }
        return null;
    }
}
