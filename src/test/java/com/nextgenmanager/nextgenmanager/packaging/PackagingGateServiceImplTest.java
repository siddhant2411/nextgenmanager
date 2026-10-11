package com.nextgenmanager.nextgenmanager.packaging;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageLine;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.packaging.service.PackagingGateServiceImpl;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The gate on closing a packing slip.
 *
 * <p>A box whose inspection failed or is unjudged has always stopped the slip. What was missing
 * was a way to say an item may never leave uninspected: a box was looked at only when somebody
 * remembered to ask.
 */
@ExtendWith(MockitoExtension.class)
class PackagingGateServiceImplTest {

    @Mock private InspectionLotRepository inspectionLotRepository;

    @InjectMocks private PackagingGateServiceImpl gate;

    private static final long BOX_ID = 30L;

    private InventoryItem disc;
    private PackingSlip slip;

    @BeforeEach
    void setUp() {
        disc = new InventoryItem();
        disc.setInventoryItemId(7);
        disc.setItemCode("FG-0001");
        disc.setProductInventorySettings(new ProductInventorySettings());

        PackageBox box = new PackageBox();
        box.setId(BOX_ID);
        box.setBoxNumber(1);

        PackageLine line = new PackageLine();
        line.setPackageBox(box);
        line.setInventoryItem(disc);
        line.setQuantity(new BigDecimal("10"));
        box.getLines().add(line);

        slip = new PackingSlip();
        slip.setSlipNumber("PS/0001");
        slip.getBoxes().add(box);

        lenient().when(inspectionLotRepository.findLiveByPackageBox(BOX_ID)).thenReturn(List.of());
    }

    private InspectionLot lot(InspectionLotStatus status) {
        InspectionLot lot = new InspectionLot();
        lot.setLotNumber("QC/0009");
        lot.setSource(InspectionSource.PACKAGE);
        lot.setStatus(status);
        lot.setInventoryItem(disc);
        return lot;
    }

    @Test
    void aBoxNobodyAskedToInspectClosesFreely() {
        assertThat(gate.reasonsClosingIsBlocked(slip)).isEmpty();
    }

    @Test
    void anItemThatMustBeInspectedStopsABoxWithNoInspection() {
        disc.getProductInventorySettings().setPackageInspectionRequired(true);

        assertThatThrownBy(() -> gate.assertClosingAllowed(slip))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("box 1: FG-0001 requires a package inspection and none has been raised");
    }

    @Test
    void aPassedInspectionSatisfiesAnItemThatMustBeInspected() {
        disc.getProductInventorySettings().setPackageInspectionRequired(true);
        when(inspectionLotRepository.findLiveByPackageBox(BOX_ID))
                .thenReturn(List.of(lot(InspectionLotStatus.PASSED)));

        assertThat(gate.reasonsClosingIsBlocked(slip)).isEmpty();
    }

    @Test
    void aWaiverSatisfiesItToo() {
        disc.getProductInventorySettings().setPackageInspectionRequired(true);
        when(inspectionLotRepository.findLiveByPackageBox(BOX_ID))
                .thenReturn(List.of(lot(InspectionLotStatus.WAIVED)));

        assertThat(gate.reasonsClosingIsBlocked(slip)).isEmpty();
    }

    @Test
    void aFailedInspectionIsReportedOnceNotTwice() {
        // The failure is the reason. "None has been raised" beside it would be untrue.
        disc.getProductInventorySettings().setPackageInspectionRequired(true);
        when(inspectionLotRepository.findLiveByPackageBox(BOX_ID))
                .thenReturn(List.of(lot(InspectionLotStatus.FAILED)));

        assertThat(gate.reasonsClosingIsBlocked(slip))
                .singleElement().asString().contains("QC/0009 failed");
    }
}
