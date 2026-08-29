package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderFilter;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderItemCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderListDto;
import com.nextgenmanager.nextgenmanager.purchase.exception.PurchaseOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.purchase.mapper.PurchaseOrderMapper;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the purchase-order behaviour the PEC register import depends on.
 *
 * <p>Deliberately not a Spring test. {@code contextLoads} in this repo dies on datasource
 * configuration before JPA initialises, so anything needing a container never runs. Everything
 * here is the service against mocked repositories, which is enough for the three things that
 * actually broke: filters that cancelled each other, a GST treatment that could not be stated,
 * and a lookup by number that did not exist.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceImplTest {

    @Mock private PurchaseOrderRepository poRepo;
    @Mock private ContactRepository contactRepo;
    @Mock private InventoryItemRepository itemRepo;
    @Mock private SalesOrderRepository salesOrderRepo;
    @Mock private EntityManager em;
    @Mock private GstResolver gstResolver;
    @Mock private PurchaseOrderNumberGenerator numberGen;
    @Mock private PurchaseOrderApprovalService approvalService;
    @Mock private PurchaseOrderPdfService pdfService;
    @Mock private PurchaseOrderMapper mapper;

    private PurchaseOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        // The tax calculator is pure arithmetic with no collaborators, so the real one is used --
        // mocking it would hide the thing these tests are checking.
        service = new PurchaseOrderServiceImpl(poRepo, contactRepo, itemRepo, salesOrderRepo, em,
                gstResolver, new PurchaseOrderTaxCalculator(), numberGen, approvalService,
                pdfService, mapper);

        when(numberGen.next()).thenReturn("PO/2026-27/0001");
        when(poRepo.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any())).thenReturn(emptyDto());
        when(mapper.toListDto(any())).thenReturn(emptyListDto());
    }

    // ── Filters combine instead of cancelling ────────────────────────────────────────────────

    @Test
    void listAppliesEveryFilterTogether() {
        Page<PurchaseOrder> page = new PageImpl<>(List.of(new PurchaseOrder()));
        when(poRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PurchaseOrderFilter filter = new PurchaseOrderFilter();
        filter.setVendorId(5);
        filter.setStatus(PurchaseOrderStatus.SENT);

        service.list(filter, PageRequest.of(0, 20));

        // The old implementation returned on the first filter it recognised: vendorId won and the
        // status was never looked at, so this call answered "every PO for vendor 5" instead.
        // One specification is built now, and the branch that could drop a filter is gone.
        verify(poRepo).findAll(any(Specification.class), any(Pageable.class));
        verify(poRepo, never()).findByVendorIdAndDeletedDateIsNull(any(), any());
        verify(poRepo, never()).findByStatusAndDeletedDateIsNull(any(), any());
    }

    @Test
    void listWithoutAFilterStillWorks() {
        when(poRepo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(null, PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void filterRejectsAnInvertedDateRange() {
        PurchaseOrderFilter filter = new PurchaseOrderFilter();
        filter.setFromDate(LocalDate.of(2026, 8, 1));
        filter.setToDate(LocalDate.of(2026, 4, 1));

        // Silently returning nothing is what a range check exists to prevent -- an empty result
        // reads as "no purchases in that window" rather than "you typed the dates backwards".
        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("after");
    }

    @Test
    void filterTrimsBlanksToNull() {
        PurchaseOrderFilter filter = new PurchaseOrderFilter();
        filter.setPoNumber("   ");
        filter.setReference(" PO 26 / PURCHASE ORDER REGISTER-2026 ");

        filter.normalized();

        assertThat(filter.getPoNumber()).isNull();
        assertThat(filter.getReference()).isEqualTo("PO 26 / PURCHASE ORDER REGISTER-2026");
    }

    // ── Lookup by the number a human quotes ──────────────────────────────────────────────────

    @Test
    void getByNumberFindsAnActivePo() {
        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderNumber("PO/2026-27/0007");
        when(poRepo.findByPurchaseOrderNumberAndDeletedDateIsNull("PO/2026-27/0007"))
                .thenReturn(Optional.of(po));

        assertThat(service.getByNumber("PO/2026-27/0007")).isNotNull();
    }

    @Test
    void getByNumberIs404NotEmpty() {
        when(poRepo.findByPurchaseOrderNumberAndDeletedDateIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByNumber("PO/2026-27/9999"))
                .isInstanceOf(PurchaseOrderNotFoundException.class);
    }

    // ── GST treatment ────────────────────────────────────────────────────────────────────────

    @Test
    void vendorWithoutGstinBooksNoTaxWhenTheTreatmentIsLeftToDerive() {
        stubVendorAndItem();
        when(gstResolver.deriveGstTreatment(any())).thenReturn(GstTreatment.UNREGISTERED);

        PurchaseOrder saved = createAndCapture(null);

        // This is correct for a genuinely unregistered vendor, and it is exactly what would have
        // happened to all 185 PEC purchase orders: none of those vendors has a GSTIN on file, so
        // 18% on every line of a 1.2 crore register would have come out as zero.
        assertThat(saved.getGstTreatment()).isEqualTo(GstTreatment.UNREGISTERED);
        assertThat(saved.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.getSgstAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.getGrandTotal()).isEqualByComparingTo("1000");
    }

    @Test
    void anExplicitTreatmentOverridesTheDerivedOne() {
        stubVendorAndItem();
        when(gstResolver.deriveGstTreatment(any())).thenReturn(GstTreatment.UNREGISTERED);

        PurchaseOrder saved = createAndCapture(GstTreatment.INTRA_STATE);

        // 1,000 at 18% intra-state: 90 CGST + 90 SGST.
        assertThat(saved.getGstTreatment()).isEqualTo(GstTreatment.INTRA_STATE);
        assertThat(saved.getCgstAmount()).isEqualByComparingTo("90.00");
        assertThat(saved.getSgstAmount()).isEqualByComparingTo("90.00");
        assertThat(saved.getIgstAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.getGrandTotal()).isEqualByComparingTo("1180");
    }

    @Test
    void recalculateKeepsAnExplicitTreatment() {
        PurchaseOrder po = poWithOneLine();
        po.setGstTreatment(GstTreatment.INTRA_STATE);
        po.setVendor(vendorWithoutGstin());
        when(poRepo.findByIdAndDeletedDateIsNull(1L)).thenReturn(Optional.of(po));
        when(gstResolver.deriveGstTreatment(any())).thenReturn(GstTreatment.UNREGISTERED);

        service.recalculate(1L);

        // Re-deriving here would have silently zeroed the tax on every imported PO the first time
        // anything touched it.
        assertThat(po.getGstTreatment()).isEqualTo(GstTreatment.INTRA_STATE);
        assertThat(po.getGrandTotal()).isEqualByComparingTo("1180");
    }

    // ── Reference ────────────────────────────────────────────────────────────────────────────

    @Test
    void createStoresTheExternalReference() {
        stubVendorAndItem();

        PurchaseOrder saved = createAndCapture(GstTreatment.INTRA_STATE);

        assertThat(saved.getReference()).isEqualTo("PO 26 / PURCHASE ORDER REGISTER-2026");
    }

    // ── Line validation ──────────────────────────────────────────────────────────────────────

    @Test
    void aLineWithNoItemIdNamesTheLine() {
        stubVendorAndItem();
        PurchaseOrderCreateDto dto = new PurchaseOrderCreateDto(
                7, null, null, null, null, "INR", BigDecimal.ONE, null, null, null, null, null,
                null, null, null, null,
                List.of(new PurchaseOrderItemCreateDto(
                        null, null, null, 1, BigDecimal.TEN, null, null, null, null, null)),
                null, null, null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Line 1")
                .hasMessageContaining("itemId");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private PurchaseOrder createAndCapture(GstTreatment treatment) {
        PurchaseOrderCreateDto dto = new PurchaseOrderCreateDto(
                7, null, null, null, null, "INR", BigDecimal.ONE, null, null, null, null, null,
                null, null,
                "PO 26 / PURCHASE ORDER REGISTER-2026", treatment,
                List.of(new PurchaseOrderItemCreateDto(
                        42L, null, null, 10, new BigDecimal("100"), BigDecimal.ZERO,
                        new BigDecimal("18"), null, null, null)),
                null, null, null);

        service.create(dto);

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(poRepo).save(captor.capture());
        return captor.getValue();
    }

    private void stubVendorAndItem() {
        when(contactRepo.findById(7)).thenReturn(Optional.of(vendorWithoutGstin()));
        InventoryItem item = mock(InventoryItem.class);
        when(item.getName()).thenReturn("CF8 Casting");
        when(item.getHsnCode()).thenReturn("7325");
        when(item.getUom()).thenReturn(null);
        when(item.getProductFinanceSettings()).thenReturn(null);
        when(itemRepo.findById(42)).thenReturn(Optional.of(item));
    }

    private Contact vendorWithoutGstin() {
        Contact c = new Contact();
        c.setCompanyName("Sarswati Metal");
        return c;
    }

    private PurchaseOrder poWithOneLine() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem line =
                new com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem();
        line.setPurchaseOrder(po);
        line.setQuantityOrdered(10);
        line.setUnitPrice(new BigDecimal("100"));
        line.setGstRatePct(new BigDecimal("18"));
        po.setItems(new java.util.ArrayList<>(List.of(line)));
        return po;
    }

    private PurchaseOrderDto emptyDto() {
        return new PurchaseOrderDto(1L, "PO/2026-27/0001", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, List.of());
    }

    private PurchaseOrderListDto emptyListDto() {
        return new PurchaseOrderListDto(1L, "PO/2026-27/0001", null, null, null, null, null,
                null, null, null, null, null, 0, null, null);
    }
}
