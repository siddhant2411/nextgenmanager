package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderSummaryDTO;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderListMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderMapper;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderSummaryTest {

    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private WorkOrderMaterialRepository workOrderMaterialRepository;
    @Mock
    private WorkOrderOperationRepository workOrderOperationRepository;
    @Mock
    private WorkOrderAuditService auditService;
    @Mock
    private RoutingService routingService;
    @Mock
    private BomService bomService;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderListMapper workOrderListMapper;

    @InjectMocks
    private WorkOrderServiceImpl service;

    @Test
    void getWorkOrderSummary_countsByRules() {
        LocalDate today = LocalDate.of(2026, 2, 13);

        WorkOrder overdueActive = buildWorkOrder(1, WorkOrderStatus.IN_PROGRESS,
                dateOf(today.minusDays(1)), null);
        WorkOrder overdueCompleted = buildWorkOrder(2, WorkOrderStatus.COMPLETED,
                dateOf(today.minusDays(1)), null);
        WorkOrder dueSoonActive = buildWorkOrder(3, WorkOrderStatus.RELEASED,
                dateOf(today.plusDays(2)), null);
        WorkOrder dueSoonCancelled = buildWorkOrder(4, WorkOrderStatus.CANCELLED,
                dateOf(today.plusDays(1)), null);
        WorkOrder ready = buildWorkOrder(5, WorkOrderStatus.RELEASED, null, null);
        WorkOrder inProgress = buildWorkOrder(6, WorkOrderStatus.IN_PROGRESS, null, null);
        WorkOrder completedToday = buildWorkOrder(7, WorkOrderStatus.COMPLETED,
                null, dateOf(today));
        WorkOrder blocked = buildWorkOrder(8, WorkOrderStatus.HOLD, null, null);

        when(workOrderRepository.findAll()).thenReturn(List.of(
                overdueActive,
                overdueCompleted,
                dueSoonActive,
                dueSoonCancelled,
                ready,
                inProgress,
                completedToday,
                blocked
        ));

        when(workOrderOperationRepository.existsByWorkOrderAndStatus(any(), eq(OperationStatus.READY)))
                .thenReturn(false);
        when(workOrderOperationRepository.existsByWorkOrderAndStatus(ready, OperationStatus.READY))
                .thenReturn(true);

        WorkOrderSummaryDTO summary = service.getWorkOrderSummary(today);

        assertThat(summary.getOverdue()).isEqualTo(1);
        assertThat(summary.getDueSoon()).isEqualTo(1);
        assertThat(summary.getReady()).isEqualTo(1);
        assertThat(summary.getInProgress()).isEqualTo(2);
        assertThat(summary.getCompletingToday()).isEqualTo(1);
        assertThat(summary.getBlocked()).isEqualTo(1);
    }

    private static WorkOrder buildWorkOrder(
            int id,
            WorkOrderStatus status,
            Date dueDate,
            Date plannedEndDate
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setWorkOrderStatus(status);
        workOrder.setDueDate(dueDate);
        workOrder.setPlannedEndDate(plannedEndDate);
        return workOrder;
    }

    private static Date dateOf(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
