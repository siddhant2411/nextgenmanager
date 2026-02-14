package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.dto.IssueWorkOrderMaterialDTO;
import com.nextgenmanager.nextgenmanager.production.enums.MaterialIssueStatus;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderListMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderMapper;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceImplTest {

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
    void issueMaterials_setsCompletedQtyToMaterialLimitWhenLowerThanOperations() {
        WorkOrder workOrder = buildWorkOrder(1, "WO-1", new BigDecimal("10"), WorkOrderStatus.RELEASED);

        WorkOrderOperation op1 = buildOperation(workOrder, 1, new BigDecimal("8"), new BigDecimal("10"));
        WorkOrderOperation op2 = buildOperation(workOrder, 2, new BigDecimal("10"), new BigDecimal("10"));

        WorkOrderMaterial material = buildMaterial(workOrder, 101L, new BigDecimal("20"));

        IssueWorkOrderMaterialDTO issueDTO = new IssueWorkOrderMaterialDTO(
                1,
                List.of(new IssueWorkOrderMaterialDTO.MaterialIssueItem(
                        101L,
                        new BigDecimal("8"),
                        BigDecimal.ZERO
                ))
        );

        when(workOrderRepository.findById(1)).thenReturn(Optional.of(workOrder));
        when(workOrderMaterialRepository.findById(101L)).thenReturn(Optional.of(material));
        when(workOrderMaterialRepository.findByWorkOrder(workOrder)).thenReturn(List.of(material));
        when(workOrderOperationRepository.findByWorkOrder(workOrder)).thenReturn(List.of(op1, op2));

        service.issueMaterials(issueDTO);

        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("4");
        assertThat(material.getIssueStatus()).isEqualTo(MaterialIssueStatus.PARTIAL_ISSUED);
    }

    @Test
    void issueMaterials_setsCompletedQtyToOperationMinWhenLowerThanMaterials() {
        WorkOrder workOrder = buildWorkOrder(1, "WO-2", new BigDecimal("10"), WorkOrderStatus.RELEASED);

        WorkOrderOperation op1 = buildOperation(workOrder, 1, new BigDecimal("3"), new BigDecimal("10"));
        WorkOrderOperation op2 = buildOperation(workOrder, 2, new BigDecimal("7"), new BigDecimal("10"));

        WorkOrderMaterial material = buildMaterial(workOrder, 201L, new BigDecimal("20"));

        IssueWorkOrderMaterialDTO issueDTO = new IssueWorkOrderMaterialDTO(
                1,
                List.of(new IssueWorkOrderMaterialDTO.MaterialIssueItem(
                        201L,
                        new BigDecimal("20"),
                        BigDecimal.ZERO
                ))
        );

        when(workOrderRepository.findById(1)).thenReturn(Optional.of(workOrder));
        when(workOrderMaterialRepository.findById(201L)).thenReturn(Optional.of(material));
        when(workOrderMaterialRepository.findByWorkOrder(workOrder)).thenReturn(List.of(material));
        when(workOrderOperationRepository.findByWorkOrder(workOrder)).thenReturn(List.of(op1, op2));

        service.issueMaterials(issueDTO);

        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("3");
        assertThat(material.getIssueStatus()).isEqualTo(MaterialIssueStatus.ISSUED);
    }

    @Test
    void completeOperation_usesOperationsWhenNoMaterials() {
        WorkOrder workOrder = buildWorkOrder(1, "WO-3", new BigDecimal("10"), WorkOrderStatus.IN_PROGRESS);

        WorkOrderOperation op1 = buildOperation(workOrder, 1, new BigDecimal("2"), new BigDecimal("10"));
        op1.setId(11L);
        op1.setStatus(OperationStatus.IN_PROGRESS);
        WorkOrderOperation op2 = buildOperation(workOrder, 2, new BigDecimal("5"), new BigDecimal("10"));

        when(workOrderOperationRepository.findById(11L)).thenReturn(Optional.of(op1));
        when(workOrderOperationRepository.findByWorkOrder(workOrder)).thenReturn(List.of(op1, op2));
        when(workOrderOperationRepository.findTopByWorkOrderAndSequenceGreaterThanOrderBySequenceAsc(workOrder, 1))
                .thenReturn(null);
        when(workOrderMaterialRepository.findByWorkOrder(workOrder)).thenReturn(Collections.emptyList());

        service.completeOperation(11L, new BigDecimal("3"));

        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("3");
        assertThat(op1.getCompletedQuantity()).isEqualByComparingTo("3");
        assertThat(op1.getStatus()).isEqualTo(OperationStatus.COMPLETED);
    }

    private static WorkOrder buildWorkOrder(int id, String number, BigDecimal plannedQty, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setWorkOrderNumber(number);
        workOrder.setPlannedQuantity(plannedQty);
        workOrder.setCompletedQuantity(BigDecimal.ZERO);
        workOrder.setScrappedQuantity(BigDecimal.ZERO);
        workOrder.setWorkOrderStatus(status);
        return workOrder;
    }

    private static WorkOrderOperation buildOperation(
            WorkOrder workOrder,
            int sequence,
            BigDecimal completedQty,
            BigDecimal plannedQty
    ) {
        WorkOrderOperation op = new WorkOrderOperation();
        op.setWorkOrder(workOrder);
        op.setSequence(sequence);
        op.setCompletedQuantity(completedQty);
        op.setPlannedQuantity(plannedQty);
        op.setStatus(OperationStatus.IN_PROGRESS);
        return op;
    }

    private static WorkOrderMaterial buildMaterial(WorkOrder workOrder, Long id, BigDecimal requiredQty) {
        WorkOrderMaterial material = new WorkOrderMaterial();
        material.setId(id);
        material.setWorkOrder(workOrder);
        material.setNetRequiredQuantity(requiredQty);
        material.setIssuedQuantity(BigDecimal.ZERO);
        material.setScrappedQuantity(BigDecimal.ZERO);
        material.setIssueStatus(MaterialIssueStatus.NOT_ISSUED);

        InventoryItem item = new InventoryItem();
        item.setItemCode("ITEM-" + id);
        material.setComponent(item);
        return material;
    }
}
