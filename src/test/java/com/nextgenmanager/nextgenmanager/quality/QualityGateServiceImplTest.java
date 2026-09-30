package com.nextgenmanager.nextgenmanager.quality;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaEntry;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderQaResult;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderQaResultRepository;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import com.nextgenmanager.nextgenmanager.quality.service.QualityGateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The quality gate.
 *
 * <p>Every one of these cases used to end the same way: the work order completed and the finished
 * goods went on the shelf. The interesting question is not whether an inspection was recorded —
 * it always was — but which recorded verdicts are now allowed to stop the goods.
 */
@ExtendWith(MockitoExtension.class)
class QualityGateServiceImplTest {

    @Mock private InspectionLotRepository inspectionLotRepository;
    @Mock private WorkOrderQaResultRepository workOrderQaResultRepository;

    @InjectMocks private QualityGateServiceImpl gate;

    private static final int WO_ID = 42;
    private static final int ITEM_ID = 7;

    private WorkOrder workOrder;
    private InventoryItem valve;

    @BeforeEach
    void setUp() {
        valve = new InventoryItem();
        valve.setInventoryItemId(ITEM_ID);
        valve.setItemCode("FBTM-1112");
        valve.setName("Flush bottom valve");
        valve.setProductInventorySettings(new ProductInventorySettings());

        workOrder = new WorkOrder();
        workOrder.setId(WO_ID);
        workOrder.setWorkOrderNumber("WO-1");

        WorkOrderLine line = new WorkOrderLine();
        line.setId(1L);
        line.setLineNumber(1);
        line.setInventoryItem(valve);
        workOrder.setLines(new ArrayList<>(List.of(line)));

        lenient().when(inspectionLotRepository.findLiveByWorkOrder(WO_ID)).thenReturn(List.of());
        lenient().when(workOrderQaResultRepository.findFailedCriticalForWorkOrder(WO_ID))
                .thenReturn(List.of());
    }

    private InspectionLot lot(InspectionLotStatus status) {
        InspectionLot lot = new InspectionLot();
        lot.setId(1L);
        lot.setLotNumber("QC/0001");
        lot.setSource(InspectionSource.FINAL);
        lot.setStatus(status);
        lot.setInventoryItem(valve);
        lot.setWorkOrder(workOrder);
        lot.setQuantityOffered(new BigDecimal("10"));
        return lot;
    }

    private WorkOrderQaResult failedCritical(String parameterName) {
        WorkOrderOperation operation = new WorkOrderOperation();
        operation.setId(5L);
        operation.setWorkOrder(workOrder);

        WorkOrderQaEntry entry = new WorkOrderQaEntry();
        entry.setId(9L);
        entry.setWorkOrderOperation(operation);
        entry.setParameterName(parameterName);
        entry.setCritical(true);

        WorkOrderQaResult result = new WorkOrderQaResult();
        result.setWorkOrderQaEntry(entry);
        result.setResult(QaResult.FAIL);
        return result;
    }

    // ─── nothing in the way ───────────────────────────────────────────────────

    @Test
    void anOrderWithNoInspectionAndNoFailuresProduces() {
        assertThat(gate.reasonsProductionIsBlocked(workOrder)).isEmpty();
        assertThatCode(() -> gate.assertProductionAllowed(workOrder)).doesNotThrowAnyException();
    }

    @Test
    void anItemThatDoesNotRequireInspectionNeedsNoLot() {
        // The configured rule is off by default: a shop floor that has never raised a lot keeps
        // working exactly as it did.
        valve.getProductInventorySettings().setFinalInspectionRequired(false);

        assertThat(gate.reasonsProductionIsBlocked(workOrder)).isEmpty();
    }

    // ─── the three rules ──────────────────────────────────────────────────────

    @Test
    void aFailedFinalInspectionStopsProduction() {
        InspectionLot failed = lot(InspectionLotStatus.FAILED);
        failed.setRemarks("seat leaks at 6 bar");
        when(inspectionLotRepository.findLiveByWorkOrder(WO_ID)).thenReturn(List.of(failed));

        assertThatThrownBy(() -> gate.assertProductionAllowed(workOrder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QC/0001 failed")
                .hasMessageContaining("seat leaks at 6 bar");
    }

    @Test
    void anInspectionNobodyHasJudgedStopsProduction() {
        // Silence is not consent: somebody raised this lot because these goods were to be looked
        // at, and completing before the answer arrives defeats the point of raising it.
        when(inspectionLotRepository.findLiveByWorkOrder(WO_ID))
                .thenReturn(List.of(lot(InspectionLotStatus.PENDING)));

        assertThat(gate.reasonsProductionIsBlocked(workOrder))
                .singleElement().asString().contains("has not been judged yet");
    }

    @Test
    void aWaivedInspectionLetsTheGoodsOut() {
        // Failed, and released anyway by someone who owns that decision.
        InspectionLot waived = lot(InspectionLotStatus.WAIVED);
        waived.setWaivedBy("plant.head");
        when(inspectionLotRepository.findLiveByWorkOrder(WO_ID)).thenReturn(List.of(waived));

        assertThat(gate.reasonsProductionIsBlocked(workOrder)).isEmpty();
    }

    @Test
    void anItemRequiringInspectionCannotProduceWithoutOne() {
        valve.getProductInventorySettings().setFinalInspectionRequired(true);

        assertThat(gate.reasonsProductionIsBlocked(workOrder))
                .singleElement().asString()
                .contains("FBTM-1112 requires a final inspection and none has passed");
    }

    @Test
    void aPassedLotSatisfiesAnItemThatRequiresInspection() {
        valve.getProductInventorySettings().setFinalInspectionRequired(true);
        when(inspectionLotRepository.findLiveByWorkOrder(WO_ID))
                .thenReturn(List.of(lot(InspectionLotStatus.PASSED)));

        assertThat(gate.reasonsProductionIsBlocked(workOrder)).isEmpty();
    }

    @Test
    void aFailedCriticalCheckStopsProduction() {
        // The hole this phase was raised to close: this result has always been recorded, and the
        // work order completed anyway.
        when(workOrderQaResultRepository.findFailedCriticalForWorkOrder(WO_ID))
                .thenReturn(List.of(failedCritical("Seat leak test")));

        assertThatThrownBy(() -> gate.assertProductionAllowed(workOrder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("critical check 'Seat leak test' failed");
    }

    // ─── how it reports ───────────────────────────────────────────────────────

    @Test
    void everyReasonIsGivenAtOnce() {
        // A supervisor who fixes one problem and is only then told about the next has been sent
        // round the loop twice.
        valve.getProductInventorySettings().setFinalInspectionRequired(true);
        when(inspectionLotRepository.findLiveByWorkOrder(WO_ID))
                .thenReturn(List.of(lot(InspectionLotStatus.FAILED)));
        when(workOrderQaResultRepository.findFailedCriticalForWorkOrder(WO_ID))
                .thenReturn(List.of(failedCritical("Wall thickness")));

        assertThat(gate.reasonsProductionIsBlocked(workOrder)).hasSize(3);
        assertThatThrownBy(() -> gate.assertProductionAllowed(workOrder))
                .hasMessageContaining("QC/0001 failed")
                .hasMessageContaining("requires a final inspection")
                .hasMessageContaining("Wall thickness");
    }
}
