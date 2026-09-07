package com.nextgenmanager.nextgenmanager.quality;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.QualityStatus;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.quality.dto.NcrCreateRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NcrDispositionRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NonConformanceReportDto;
import com.nextgenmanager.nextgenmanager.quality.model.*;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import com.nextgenmanager.nextgenmanager.quality.repository.NonConformanceReportRepository;
import com.nextgenmanager.nextgenmanager.quality.service.NcrNumberGenerator;
import com.nextgenmanager.nextgenmanager.quality.service.NonConformanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * What happens to goods that failed.
 *
 * <p>A rejected quantity used to be typed onto a goods receipt and dropped. These tests are about
 * the decision that follows: who is allowed to make it, what it does to the stock, and which
 * decisions this service deliberately refuses to carry out on its own.
 */
@ExtendWith(MockitoExtension.class)
class NonConformanceServiceImplTest {

    @Mock private NonConformanceReportRepository ncrRepository;
    @Mock private InspectionLotRepository inspectionLotRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private NcrNumberGenerator numberGenerator;

    @InjectMocks private NonConformanceServiceImpl service;

    private static final int ITEM_ID = 11;

    private InventoryItem casting;
    private InspectionLot failedLot;
    private InventoryInstance rejectedStock;

    @BeforeEach
    void setUp() {
        casting = new InventoryItem();
        casting.setInventoryItemId(ITEM_ID);
        casting.setItemCode("RMCF80001");
        casting.setName("Raw casting");

        GoodsReceiptNote grn = new GoodsReceiptNote();
        grn.setId(500L);

        failedLot = new InspectionLot();
        failedLot.setId(1L);
        failedLot.setLotNumber("QC/0007");
        failedLot.setSource(InspectionSource.INCOMING);
        failedLot.setStatus(InspectionLotStatus.FAILED);
        failedLot.setInventoryItem(casting);
        failedLot.setGoodsReceiptNote(grn);
        failedLot.setQuantityOffered(new BigDecimal("10"));

        rejectedStock = new InventoryInstance();
        rejectedStock.setId(90L);
        rejectedStock.setInventoryItem(casting);
        rejectedStock.setQuantity(new BigDecimal("4"));
        rejectedStock.setQualityStatus(QualityStatus.FAILED);

        lenient().when(numberGenerator.next()).thenReturn("NCR/0001");
        lenient().when(ncrRepository.save(any(NonConformanceReport.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(inspectionLotRepository.findLiveById(1L)).thenReturn(Optional.of(failedLot));
        lenient().when(inventoryInstanceRepository.findFailedStock(ITEM_ID))
                .thenReturn(List.of(rejectedStock));
    }

    private NonConformanceReport openReport() {
        NonConformanceReport ncr = new NonConformanceReport();
        ncr.setId(2L);
        ncr.setNcrNumber("NCR/0001");
        ncr.setInspectionLot(failedLot);
        ncr.setQuantity(new BigDecimal("4"));
        ncr.setProblem("porosity on the flange face");
        ncr.setStatus(NcrStatus.OPEN);
        lenient().when(ncrRepository.findLiveById(2L)).thenReturn(Optional.of(ncr));
        return ncr;
    }

    // ─── raising ──────────────────────────────────────────────────────────────

    @Test
    void aReportIsRaisedAgainstAFailedInspection() {
        NonConformanceReportDto ncr = service.raise(new NcrCreateRequest(
                1L, new BigDecimal("4"), "porosity on the flange face", "qc.ravi", null));

        assertThat(ncr.ncrNumber()).isEqualTo("NCR/0001");
        assertThat(ncr.status()).isEqualTo(NcrStatus.OPEN);
        assertThat(ncr.inspectionLotNumber()).isEqualTo("QC/0007");
        assertThat(ncr.disposition()).isNull();
    }

    @Test
    void aReportCannotBeRaisedAgainstAnInspectionThatPassed() {
        failedLot.setStatus(InspectionLotStatus.PASSED);

        assertThatThrownBy(() -> service.raise(new NcrCreateRequest(
                1L, BigDecimal.ONE, "something", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raised against an inspection that failed");
    }

    @Test
    void aReportHasToSayWhatIsWrong() {
        assertThatThrownBy(() -> service.raise(new NcrCreateRequest(
                1L, BigDecimal.ONE, "   ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Say what is wrong");
    }

    @Test
    void aReportCannotCoverMoreThanWasInspected() {
        assertThatThrownBy(() -> service.raise(new NcrCreateRequest(
                1L, new BigDecimal("11"), "porosity", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only offered 10");
    }

    // ─── deciding ─────────────────────────────────────────────────────────────

    @Test
    void usingGoodsAsTheyAreNeedsSomebodysName() {
        openReport();

        assertThatThrownBy(() -> service.decide(2L, new NcrDispositionRequest(
                NcrDisposition.USE_AS_IS, "supervisor", null, "customer accepted it")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a name against it");
    }

    @Test
    void useAsIsReleasesTheStockAsWaived() {
        openReport();

        NonConformanceReportDto decided = service.decide(2L, new NcrDispositionRequest(
                NcrDisposition.USE_AS_IS, "supervisor", "plant.head", "customer accepted it"));

        assertThat(decided.status()).isEqualTo(NcrStatus.CLOSED);
        assertThat(decided.approvedBy()).isEqualTo("plant.head");
        // WAIVED rather than PASSED: what happened was a release, not an inspection that passed.
        assertThat(rejectedStock.getQualityStatus()).isEqualTo(QualityStatus.WAIVED);
    }

    @Test
    void reworkSendsTheStockBackForInspection() {
        openReport();

        service.decide(2L, new NcrDispositionRequest(NcrDisposition.REWORK, "supervisor", null, null));

        assertThat(rejectedStock.getQualityStatus()).isEqualTo(QualityStatus.PENDING_QC);
    }

    @Test
    void scrapLeavesTheStockUnusableRatherThanWritingItOff() {
        // Writing stock off is an adjustment with its own document. Inventing the movement here
        // would put a quantity change in the ledger that nothing explains.
        openReport();

        service.decide(2L, new NcrDispositionRequest(NcrDisposition.SCRAP, "supervisor", null, null));

        assertThat(rejectedStock.getQualityStatus()).isEqualTo(QualityStatus.FAILED);
    }

    @Test
    void aClosedReportIsNotDecidedTwice() {
        NonConformanceReport ncr = openReport();
        ncr.setStatus(NcrStatus.CLOSED);
        ncr.setDisposition(NcrDisposition.SCRAP);

        assertThatThrownBy(() -> service.decide(2L, new NcrDispositionRequest(
                NcrDisposition.REWORK, "someone", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is closed: it was SCRAP");
    }
}
