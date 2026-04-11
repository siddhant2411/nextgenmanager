package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.dto.IssueWorkOrderMaterialDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderRequestDTO;
import com.nextgenmanager.nextgenmanager.production.enums.*;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderListMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderMapper;
import com.nextgenmanager.nextgenmanager.production.model.*;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.*;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private WorkCenterRepository workCenterRepository;
    @Mock
    private TestTemplateService testTemplateService;
    @Mock
    private WorkOrderTestResultRepository workOrderTestResultRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
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

    @Test
    void addWorkOrder_throwsWhenBomIdMissing() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setBomId(null);

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOM ID is required");
    }

    @Test
    void addWorkOrder_throwsWhenPlannedQuantityMissing() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setPlannedQuantity(null);

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned quantity must be greater than zero");
    }

    @Test
    void addWorkOrder_throwsWhenPlannedQuantityNonPositive() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setPlannedQuantity(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned quantity must be greater than zero");
    }

    @Test
    void addWorkOrder_throwsWhenBomNotFound() {
        WorkOrderRequestDTO dto = baseRequest();
        when(bomService.getBom(dto.getBomId())).thenReturn(null);

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("BOM not found");
    }

    @Test
    void addWorkOrder_throwsWhenRoutingNotFound() {
        WorkOrderRequestDTO dto = baseRequest();
        Bom bom = buildBomWithPositions(new ArrayList<>(), null);
        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(null);

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No routing found");
    }

    @Test
    void addWorkOrder_allowsWhenBomHasNoPositionsButRoutingHasOperations() {
        WorkOrderRequestDTO dto = baseRequest();
        Bom bom = buildBomWithPositions(Collections.emptyList(), null);
        Routing routing = buildRoutingWithOperations(
                List.of(routingOperation(1L, 1, "Cut", null, BigDecimal.ONE, BigDecimal.ONE, null)),
                bom
        );

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderMapper.toDTO(any(WorkOrder.class))).thenReturn(new WorkOrderDTO());

        service.addWorkOrder(dto);

        ArgumentCaptor<List> operationsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> materialsCaptor = ArgumentCaptor.forClass(List.class);

        verify(workOrderOperationRepository).saveAll(operationsCaptor.capture());
        verify(workOrderMaterialRepository).saveAll(materialsCaptor.capture());

        assertThat((List<WorkOrderOperation>) operationsCaptor.getValue()).hasSize(1);
        assertThat((List<WorkOrderMaterial>) materialsCaptor.getValue()).isEmpty();
    }

    @Test
    void addWorkOrder_allowsWhenRoutingHasNoOperationsButBomHasPositions() {
        WorkOrderRequestDTO dto = baseRequest();
        Bom bom = buildBomWithPositions(List.of(bomPosition(null, 2.0, null, null, "C-1", "Comp 1")), null);
        Routing routing = buildRoutingWithOperations(Collections.emptyList(), bom);

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderMapper.toDTO(any(WorkOrder.class))).thenReturn(new WorkOrderDTO());

        service.addWorkOrder(dto);

        ArgumentCaptor<List> operationsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> materialsCaptor = ArgumentCaptor.forClass(List.class);

        verify(workOrderOperationRepository).saveAll(operationsCaptor.capture());
        verify(workOrderMaterialRepository).saveAll(materialsCaptor.capture());

        assertThat((List<WorkOrderOperation>) operationsCaptor.getValue()).isEmpty();
        assertThat((List<WorkOrderMaterial>) materialsCaptor.getValue()).hasSize(1);
    }

    @Test
    void addWorkOrder_throwsWhenBomHasNoPositionsAndRoutingHasNoOperations() {
        WorkOrderRequestDTO dto = baseRequest();
        Bom bom = buildBomWithPositions(Collections.emptyList(), null);
        Routing routing = buildRoutingWithOperations(Collections.emptyList(), bom);

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOM has no Material OR Operations");
    }

    @Test
    void addWorkOrder_throwsWhenSalesOrderMissing() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setSalesOrderId(11);

        Bom bom = buildBomWithPositions(List.of(bomPosition(null, 2.0, null, null, "C-1", "Comp 1")), null);
        Routing routing = buildRoutingWithOperations(List.of(routingOperation(1L, 1, "Cut", null, BigDecimal.ONE, BigDecimal.ONE, null)), bom);

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(salesOrderRepository.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Sales Order not found");
    }

    @Test
    void addWorkOrder_throwsWhenParentWorkOrderMissing() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setParentWorkOrderId(21);

        Bom bom = buildBomWithPositions(List.of(bomPosition(null, 2.0, null, null, "C-1", "Comp 1")), null);
        Routing routing = buildRoutingWithOperations(List.of(routingOperation(1L, 1, "Cut", null, BigDecimal.ONE, BigDecimal.ONE, null)), bom);

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(workOrderRepository.findById(21)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Parent WorkOrder not found");
    }

    @Test
    void addWorkOrder_throwsWhenWorkCenterMissing() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setWorkCenterId(31);

        Bom bom = buildBomWithPositions(List.of(bomPosition(null, 2.0, null, null, "C-1", "Comp 1")), null);
        Routing routing = buildRoutingWithOperations(List.of(routingOperation(1L, 1, "Cut", null, BigDecimal.ONE, BigDecimal.ONE, null)), bom);

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(workCenterRepository.findById(31)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addWorkOrder(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("WorkCenter not found");
    }

    @Test
    void addWorkOrder_createsWorkOrderWithOperationsMaterialsTestsAndLinks() {
        WorkOrderRequestDTO dto = baseRequest();
        dto.setSalesOrderId(11);
        dto.setParentWorkOrderId(22);
        dto.setWorkCenterId(33);
        dto.setPriority(WorkOrderPriority.HIGH);
        dto.setSourceType(WorkOrderSourceType.SALES_ORDER);
        dto.setRemarks("Urgent");

        InventoryItem finishedItem = inventoryItem(77, "FIN-77", "Finished Item");
        Bom bom = buildBomWithPositions(new ArrayList<>(), finishedItem);

        WorkCenter opCenter = new WorkCenter();
        opCenter.setId(9);
        opCenter.setCenterCode("WC-9");
        opCenter.setCenterName("Center 9");

        MachineDetails machine = new MachineDetails();
        machine.setId(5L);

        RoutingOperation op1 = routingOperation(101L, 1, "Cut", opCenter, new BigDecimal("2"), new BigDecimal("1.5"), machine);
        RoutingOperation op2 = routingOperation(102L, 2, "Weld", opCenter, null, null, null);

        Routing routing = buildRoutingWithOperations(List.of(op1, op2), bom);

        BomPosition pos1 = bomPosition(bom, 2.0, new BigDecimal("10"), op1, "C-1", "Comp 1");
        RoutingOperation missingOp = routingOperation(999L, 99, "Polish", opCenter, BigDecimal.ONE, BigDecimal.ONE, null);
        BomPosition pos2 = bomPosition(bom, 1.5, null, missingOp, "C-2", "Comp 2");
        BomPosition pos3 = bomPosition(bom, 1.0, BigDecimal.ZERO, null, "C-3", "Comp 3");
        bom.setPositions(List.of(pos1, pos2, pos3));

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(11L);
        WorkOrder parent = new WorkOrder();
        parent.setId(22);
        WorkCenter headerCenter = new WorkCenter();
        headerCenter.setId(33);
        headerCenter.setCenterCode("WC-33");
        headerCenter.setCenterName("Header Center");

        TestTemplate template = testTemplate(555L, finishedItem);
        WorkOrderDTO mapped = new WorkOrderDTO();
        mapped.setWorkOrderNumber("WO-42");

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(salesOrderRepository.findById(11L)).thenReturn(Optional.of(salesOrder));
        when(workOrderRepository.findById(22)).thenReturn(Optional.of(parent));
        when(workCenterRepository.findById(33)).thenReturn(Optional.of(headerCenter));
        when(testTemplateService.getActiveTemplatesForItem(77)).thenReturn(List.of(template));
        when(workOrderRepository.getNextWorkOrderSequence()).thenReturn(42L);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder saved = invocation.getArgument(0);
            if (saved.getId() == 0) {
                saved.setId(500);
            }
            return saved;
        });
        when(workOrderMapper.toDTO(any(WorkOrder.class))).thenReturn(mapped);

        WorkOrderDTO result = service.addWorkOrder(dto);

        assertThat(result.getWorkOrderNumber()).isEqualTo("WO-42");

        ArgumentCaptor<List> operationsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> materialsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> testsCaptor = ArgumentCaptor.forClass(List.class);

        verify(workOrderOperationRepository).saveAll(operationsCaptor.capture());
        verify(workOrderMaterialRepository).saveAll(materialsCaptor.capture());
        verify(workOrderTestResultRepository).saveAll(testsCaptor.capture());
        verify(auditService).record(any(WorkOrder.class), any(WorkOrderEventType.class), any(), any(), any(), any());

        List<WorkOrderOperation> savedOps = (List<WorkOrderOperation>) operationsCaptor.getValue();
        WorkOrderOperation firstOp = savedOps.stream().filter(op -> op.getSequence() == 1).findFirst().orElseThrow();
        WorkOrderOperation secondOp = savedOps.stream().filter(op -> op.getSequence() == 2).findFirst().orElseThrow();

        assertThat(firstOp.getStatus()).isEqualTo(OperationStatus.READY);
        assertThat(firstOp.getAvailableInputQuantity()).isEqualByComparingTo("10");
        assertThat(firstOp.getAssignedMachine()).isEqualTo(machine);
        assertThat(secondOp.getStatus()).isEqualTo(OperationStatus.PLANNED);

        List<WorkOrderMaterial> savedMaterials = (List<WorkOrderMaterial>) materialsCaptor.getValue();
        WorkOrderMaterial mat1 = savedMaterials.stream().filter(m -> "C-1".equals(m.getComponent().getItemCode())).findFirst().orElseThrow();
        WorkOrderMaterial mat2 = savedMaterials.stream().filter(m -> "C-2".equals(m.getComponent().getItemCode())).findFirst().orElseThrow();
        WorkOrderMaterial mat3 = savedMaterials.stream().filter(m -> "C-3".equals(m.getComponent().getItemCode())).findFirst().orElseThrow();

        assertThat(mat1.getWorkOrderOperation()).isEqualTo(firstOp);
        assertThat(mat2.getWorkOrderOperation()).isEqualTo(firstOp);
        assertThat(mat3.getWorkOrderOperation()).isEqualTo(firstOp);
        assertThat(mat1.getNetRequiredQuantity()).isEqualByComparingTo("20.00000");
        assertThat(mat1.getPlannedRequiredQuantity()).isEqualByComparingTo("22.00000");
        assertThat(mat2.getScrappercent()).isEqualByComparingTo("0");
        assertThat(mat1.getIssueStatus()).isEqualTo(MaterialIssueStatus.NOT_ISSUED);

        List<WorkOrderTestResult> savedTests = (List<WorkOrderTestResult>) testsCaptor.getValue();
        assertThat(savedTests).hasSize(1);
        WorkOrderTestResult tr = savedTests.get(0);
        assertThat(tr.getTestName()).isEqualTo(template.getTestName());
        assertThat(tr.getInspectionType()).isEqualTo(template.getInspectionType());
        assertThat(tr.getUnitOfMeasure()).isEqualTo(template.getUnitOfMeasure());
    }

    @Test
    void addWorkOrder_doesNotSaveTemplatesWhenNoneActive() {
        WorkOrderRequestDTO dto = baseRequest();
        InventoryItem finishedItem = inventoryItem(88, "FIN-88", "Finished");
        Bom bom = buildBomWithPositions(List.of(bomPosition(null, 1.0, null, null, "C-1", "Comp 1")), finishedItem);
        Routing routing = buildRoutingWithOperations(List.of(routingOperation(1L, 1, "Cut", null, BigDecimal.ONE, BigDecimal.ONE, null)), bom);

        when(bomService.getBom(dto.getBomId())).thenReturn(bom);
        when(routingService.getRoutingEntityByBom(bom.getId())).thenReturn(routing);
        when(testTemplateService.getActiveTemplatesForItem(88)).thenReturn(Collections.emptyList());
        when(workOrderRepository.getNextWorkOrderSequence()).thenReturn(2L);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderMapper.toDTO(any(WorkOrder.class))).thenReturn(new WorkOrderDTO());

        service.addWorkOrder(dto);

        verify(workOrderTestResultRepository, never()).saveAll(any());
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

    private static WorkOrderRequestDTO baseRequest() {
        WorkOrderRequestDTO dto = new WorkOrderRequestDTO();
        dto.setBomId(1);
        dto.setPlannedQuantity(new BigDecimal("10"));
        return dto;
    }

    private static Bom buildBomWithPositions(List<BomPosition> positions, InventoryItem parentItem) {
        Bom bom = new Bom();
        bom.setId(1);
        bom.setPositions(positions);
        bom.setParentInventoryItem(parentItem);
        return bom;
    }

    private static Routing buildRoutingWithOperations(List<RoutingOperation> operations, Bom bom) {
        Routing routing = new Routing();
        routing.setId(10L);
        routing.setBom(bom);
        routing.setOperations(operations);
        return routing;
    }

    private static RoutingOperation routingOperation(
            Long id,
            int sequence,
            String name,
            WorkCenter workCenter,
            BigDecimal setupTime,
            BigDecimal runTime,
            MachineDetails machineDetails
    ) {
        RoutingOperation op = new RoutingOperation();
        op.setId(id);
        op.setSequenceNumber(sequence);
        op.setName(name);
        op.setWorkCenter(workCenter);
        op.setSetupTime(setupTime);
        op.setRunTime(runTime);
        op.setMachineDetails(machineDetails);
        return op;
    }

    private static BomPosition bomPosition(
            Bom parentBom,
            double qty,
            BigDecimal scrap,
            RoutingOperation routingOperation,
            String componentCode,
            String componentName
    ) {
        BomPosition pos = new BomPosition();
        pos.setParentBom(parentBom);
        pos.setQuantity(qty);
        pos.setScrapPercentage(scrap);
        pos.setRoutingOperation(routingOperation);

        InventoryItem component = inventoryItem(100, componentCode, componentName);
        pos.setChildInventoryItem(component);
        return pos;
    }

    private static InventoryItem inventoryItem(int id, String code, String name) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        item.setItemCode(code);
        item.setName(name);
        return item;
    }

    private static TestTemplate testTemplate(Long id, InventoryItem item) {
        TestTemplate tmpl = new TestTemplate();
        tmpl.setId(id);
        tmpl.setInventoryItem(item);
        tmpl.setTestName("Dim Check");
        tmpl.setInspectionType(com.nextgenmanager.nextgenmanager.production.enums.InspectionType.VISUAL);
        tmpl.setSampleSize(5);
        tmpl.setIsMandatory(true);
        tmpl.setSequence(1);
        tmpl.setAcceptanceCriteria("OK");
        tmpl.setUnitOfMeasure("mm");
        tmpl.setMinValue(new BigDecimal("1.0"));
        tmpl.setMaxValue(new BigDecimal("5.0"));
        return tmpl;
    }
}
