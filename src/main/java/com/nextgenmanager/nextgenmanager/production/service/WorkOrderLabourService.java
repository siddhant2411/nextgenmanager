package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLabourEntryDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderLabourEntryRequestDTO;

import java.util.List;

public interface WorkOrderLabourService {

    WorkOrderLabourEntryDTO logLabour(Long operationId, WorkOrderLabourEntryRequestDTO request);

    List<WorkOrderLabourEntryDTO> getEntriesForOperation(Long operationId);

    List<WorkOrderLabourEntryDTO> getEntriesForWorkOrder(Long workOrderId);

    WorkOrderLabourEntryDTO updateEntry(Long entryId, WorkOrderLabourEntryRequestDTO request);

    void deleteEntry(Long entryId);
}
