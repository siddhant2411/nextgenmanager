package com.nextgenmanager.nextgenmanager.production.service.workorder;


import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

public interface WorkOrderService {

    public WorkOrderDTO getWorkOrder(int id);


    public WorkOrderDTO addWorkOrder(WorkOrderRequestDTO workOrderRequestDTO);

    public Page<WorkOrderListDTO> getAllWorkOrders(FilterRequest filterRequest);

    /** Preview the next work order number without consuming the sequence. */
    public String nextNumber();


    public WorkOrderDTO updateWorkOrder(int workOrderId, WorkOrderRequestDTO dto);

    public WorkOrderDTO releaseWorkOrder(int workOrderId, boolean forceRelease);

    /**
     * Moves quantity off a work order into a new one, returning the work order that was created.
     * See the implementation for the rules governing produced units and issued material.
     */
    public WorkOrderDTO splitWorkOrder(int workOrderId, WorkOrderSplitRequestDTO dto);

    /** Every work order linked to this one — what it came out of, and what came out of it. */
    public List<RelatedWorkOrderDTO> getRelatedWorkOrders(int workOrderId);

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
    public List<String> completeOperationPartial(PartialOperationCompleteDTO partialCompleteDTO);

    public void completeOperation(Long operationId, BigDecimal completedQty);

    public void completeWorkOrder(int workOrderId);

    public void closeWorkOrder(int workOrderId);

    public void cancelWorkOrder(int workOrderId);

    public void softDeleteWorkOrder(int workOrderId, String reason);

    /**
     * Short-close a work order before full completion.
     * Accepts partial output, returns unused materials to store,
     * and cancels remaining inventory reservations.
     *
     * @param workOrderId the work order to short-close
     * @param remarks     reason for short closure (e.g. "Tool breakage", "Priority changed")
     */
    public void shortCloseWorkOrder(int workOrderId, String remarks);

    public List<WorkOrderHistoryDTO> getWorkOrderHistory(int workOrderId);

    public WorkOrderSummaryDTO getWorkOrderSummary();

    // Scheduling
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId);

    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, java.util.Date newStartDate);

    // Material Re-order
    public WorkOrderMaterialReorderDTO reorderMaterial(Long workOrderId, Long materialId, ReorderMaterialRequestDTO dto);

    public List<WorkOrderMaterialReorderDTO> getMaterialReorders(Long workOrderId, Long materialId);
}
