package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

public interface WorkOrderService {

    public WorkOrderDTO getWorkOrder(int id);


    public WorkOrderDTO addWorkOrder(WorkOrderRequestDTO workOrderRequestDTO);

    public Page<WorkOrderListDTO> getAllWorkOrders(FilterRequest filterRequest);


    public WorkOrderDTO updateWorkOrder(int workOrderId, WorkOrderRequestDTO dto);

    public WorkOrderDTO releaseWorkOrder(int workOrderId);

    public void startOperation(Long operationId);

    /**
     * Issue materials for a work order with support for partial issuance
     * @param issueDTO Contains work order ID and list of materials to issue
     */
    public void issueMaterials(IssueWorkOrderMaterialDTO issueDTO);

    /**
     * Complete operation with partial quantity support
     * Updates completed quantity incrementally and marks operation as completed
     * when all planned quantity is met
     * @param partialCompleteDTO Contains operation ID and quantity completed
     */
    public void completeOperationPartial(PartialOperationCompleteDTO partialCompleteDTO);

    public void completeOperation(Long operationId, BigDecimal completedQty);

    public void completeWorkOrder(int workOrderId);

    public void closeWorkOrder(int workOrderId);

    public void cancelWorkOrder(int workOrderId);

    public void softDeleteWorkOrder(int workOrderId, String reason);

    public List<WorkOrderHistoryDTO> getWorkOrderHistory(int workOrderId);

    public WorkOrderSummaryDTO getWorkOrderSummary();

    // Scheduling
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId);

    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, java.util.Date newStartDate);
}
