package com.nextgenmanager.nextgenmanager.production.service.workorder;

import com.nextgenmanager.nextgenmanager.bom.enums.QaParameterType;
import com.nextgenmanager.nextgenmanager.bom.model.BomQaParameter;
import com.nextgenmanager.nextgenmanager.bom.repository.BomQaParameterRepository;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaEntry;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaResult;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderQaEntryRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderQaResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkOrderQaServiceImpl implements WorkOrderQaService {

    @Autowired private WorkOrderQaEntryRepository qaEntryRepository;
    @Autowired private WorkOrderQaResultRepository qaResultRepository;
    @Autowired private BomQaParameterRepository bomQaParameterRepository;

    @Override
    @Transactional
    public void createSnapshotsForOperation(WorkOrderOperation operation) {
        if (operation.getRoutingOperation() == null) return;
        if (!Boolean.TRUE.equals(operation.getRoutingOperation().getInspection())) return;

        List<BomQaParameter> params = bomQaParameterRepository
                .findByRoutingOperationIdAndDeletedDateIsNull(operation.getRoutingOperation().getId());

        for (BomQaParameter param : params) {
            WorkOrderQaEntry entry = WorkOrderQaEntry.builder()
                    .workOrderOperation(operation)
                    .bomQaParameterId(param.getId())
                    .parameterName(param.getName())
                    .parameterType(param.getParameterType())
                    .minValue(param.getMinValue())
                    .maxValue(param.getMaxValue())
                    .unit(param.getUnit())
                    .critical(param.getCritical())
                    .build();
            qaEntryRepository.save(entry);
        }
    }

    @Override
    public List<WorkOrderQaEntryDTO> getEntriesForOperation(Long workOrderOperationId) {
        return qaEntryRepository.findByWorkOrderOperationId(workOrderOperationId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<WorkOrderQaEntryDTO> getEntriesForWorkOrder(int workOrderId) {
        return qaEntryRepository.findByWorkOrderId(workOrderId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<WorkOrderQaEntryDTO> submitBatch(Long workOrderOperationId, WorkOrderQaBatchRequestDTO request) {
        List<WorkOrderQaEntry> entries = qaEntryRepository.findByWorkOrderOperationId(workOrderOperationId);
        Map<Long, WorkOrderQaEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(WorkOrderQaEntry::getId, e -> e));

        Date now = new Date();
        for (WorkOrderQaItemRequestDTO item : request.getItems()) {
            WorkOrderQaEntry entry = entryMap.get(item.getEntryId());
            if (entry == null) continue;

            boolean passed = evaluatePassed(entry, item);
            WorkOrderQaResult result = WorkOrderQaResult.builder()
                    .workOrderQaEntry(entry)
                    .checkedQuantity(request.getCheckedQuantity())
                    .actualValue(item.getActualValue())
                    .passed(passed)
                    .result(passed ? QaResult.PASS : QaResult.FAIL)
                    .remarks(item.getRemarks())
                    .checkedBy(request.getCheckedBy())
                    .checkedAt(now)
                    .build();
            qaResultRepository.save(result);
        }

        // Return fresh entries with updated results
        return qaEntryRepository.findByWorkOrderOperationId(workOrderOperationId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    private boolean evaluatePassed(WorkOrderQaEntry entry, WorkOrderQaItemRequestDTO item) {
        if (entry.getParameterType() == QaParameterType.PASS_FAIL) {
            return Boolean.TRUE.equals(item.getPassed());
        }
        BigDecimal actual = item.getActualValue();
        if (actual == null) return false;
        boolean aboveMin = entry.getMinValue() == null || actual.compareTo(entry.getMinValue()) >= 0;
        boolean belowMax = entry.getMaxValue() == null || actual.compareTo(entry.getMaxValue()) <= 0;
        return aboveMin && belowMax;
    }

    private WorkOrderQaEntryDTO toDTO(WorkOrderQaEntry e) {
        List<WorkOrderQaResultDTO> results = e.getResults().stream().map(r ->
                WorkOrderQaResultDTO.builder()
                        .id(r.getId())
                        .workOrderQaEntryId(e.getId())
                        .checkedQuantity(r.getCheckedQuantity())
                        .actualValue(r.getActualValue())
                        .passed(r.getPassed())
                        .result(r.getResult())
                        .remarks(r.getRemarks())
                        .checkedBy(r.getCheckedBy())
                        .checkedAt(r.getCheckedAt())
                        .creationDate(r.getCreationDate())
                        .build()
        ).collect(Collectors.toList());

        QaResult overall = computeOverall(results);

        return WorkOrderQaEntryDTO.builder()
                .id(e.getId())
                .workOrderOperationId(e.getWorkOrderOperation().getId())
                .bomQaParameterId(e.getBomQaParameterId())
                .parameterName(e.getParameterName())
                .parameterType(e.getParameterType())
                .minValue(e.getMinValue())
                .maxValue(e.getMaxValue())
                .unit(e.getUnit())
                .critical(e.getCritical())
                .results(results)
                .overallResult(overall)
                .creationDate(e.getCreationDate())
                .build();
    }

    private QaResult computeOverall(List<WorkOrderQaResultDTO> results) {
        if (results == null || results.isEmpty()) return QaResult.PENDING;
        if (results.stream().anyMatch(r -> r.getResult() == QaResult.FAIL)) return QaResult.FAIL;
        if (results.stream().allMatch(r -> r.getResult() == QaResult.PASS)) return QaResult.PASS;
        return QaResult.PENDING;
    }
}
