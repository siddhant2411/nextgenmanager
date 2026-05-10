package com.nextgenmanager.nextgenmanager.production.service.workorder;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderQaBatchRequestDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderQaEntryDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;

import java.util.List;

public interface WorkOrderQaService {

    void createSnapshotsForOperation(WorkOrderOperation operation);

    List<WorkOrderQaEntryDTO> getEntriesForOperation(Long workOrderOperationId);

    List<WorkOrderQaEntryDTO> getEntriesForWorkOrder(int workOrderId);

    List<WorkOrderQaEntryDTO> submitBatch(Long workOrderOperationId, WorkOrderQaBatchRequestDTO request);
}
