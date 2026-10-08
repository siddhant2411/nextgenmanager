package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.company.BrandingFixtures;
import com.nextgenmanager.nextgenmanager.company.BrandingFixtures.Brand;
import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.service.QuotationServiceImp;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageLine;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackingSlipRepository;
import com.nextgenmanager.nextgenmanager.packaging.service.PackingSlipPdfService;
import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanDTO;
import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;
import com.nextgenmanager.nextgenmanager.production.model.JobWorkChallan;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.JobWorkChallanRepository;
import com.nextgenmanager.nextgenmanager.production.service.jobwork.JobWorkChallanExportServiceImpl;
import com.nextgenmanager.nextgenmanager.production.service.jobwork.JobWorkChallanService;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.workorder.WorkOrderExportServiceImpl;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import com.nextgenmanager.nextgenmanager.purchase.service.PurchaseOrderPdfService;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNoteItem;
import com.nextgenmanager.nextgenmanager.sales.model.InvoiceItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
import com.nextgenmanager.nextgenmanager.sales.service.DeliveryNotePdfService;
import com.nextgenmanager.nextgenmanager.sales.service.InvoicePdfBrandingTest;
import com.nextgenmanager.nextgenmanager.sales.service.TaxInvoicePdfService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.brandingService;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.companyRepository;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.engine;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.imagesOnFirstSheet;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.sheets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every branded document, rendered in every branding mode.
 *
 * <p>These documents flow from page to page, so there is no page count to protect as there is for
 * the invoice. What is checked is that each one still renders, still carries its own content, and
 * shows the company the way the mode says: the name as text, or an image in its place.
 */
class BrandedDocumentsRenderTest {

    private static final String COMPANY = "Vajra Auto Components Private Limited";

    /** How a document shows who it is from. */
    enum Identity {
        /** A text header that a letterhead or pre-printed band replaces outright. */
        HEADER,
        /** A seller box that stays put; the letterhead goes above it. */
        SELLER_BOX,
        /** The job-work challan: a letterhead goes on top and the name is dropped, but its address band stays. */
        CHALLAN,
        /** A shop-floor sheet: always the company name, with the logo beside it when there is one. */
        SHOP_FLOOR
    }

    interface Renderer {
        byte[] render(Brand brand) throws Exception;
    }

    record Doc(String name, Identity identity, String content, int ownImages, Renderer renderer) {
        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Doc> DOCS = List.of(
            new Doc("purchase order", Identity.HEADER, "PO/2026-27/0007", 0, BrandedDocumentsRenderTest::purchaseOrder),
            new Doc("quotation", Identity.HEADER, "QTN-0031", 0, BrandedDocumentsRenderTest::quotation),
            new Doc("branding preview", Identity.HEADER, "SAMPLE DOCUMENT", 0, brand -> brandingService(brand.branding()).previewPdf(null)),
            new Doc("tax invoice", Identity.SELLER_BOX, "INV/2026-27/0101", 0, BrandedDocumentsRenderTest::taxInvoice),
            new Doc("delivery challan", Identity.SELLER_BOX, "DC/2026-27/0055", 0, BrandedDocumentsRenderTest::deliveryNote),
            new Doc("packing list", Identity.SELLER_BOX, "PKS-0012", 0, BrandedDocumentsRenderTest::packingList),
            new Doc("job-work challan", Identity.CHALLAN, "JWC/2026-27/0003", 1, BrandedDocumentsRenderTest::jobWorkChallan),
            new Doc("material pick list", Identity.SHOP_FLOOR, "WO-2026-0450", 1, BrandedDocumentsRenderTest::pickList));

    static Stream<Arguments> everyDocumentInEveryMode() {
        return DOCS.stream().flatMap(doc -> BrandingFixtures.brands().stream().map(brand -> Arguments.of(doc, brand)));
    }

    @ParameterizedTest(name = "{0} | {1}")
    @MethodSource("everyDocumentInEveryMode")
    void rendersWithTheCompanyShownAsTheModeSays(Doc doc, Brand brand) throws Exception {
        byte[] pdf = doc.renderer().render(brand);
        BrandingFixtures.sample(doc.name(), brand, pdf);

        // Long names wrap, so compare with line breaks flattened.
        List<String> sheets = sheets(pdf);
        String first = sheets.get(0).replaceAll("\\s+", " ");
        assertThat(first).contains(doc.content());

        // A header must never be what tips a document onto another sheet.
        List<String> asPlainText = sheets(doc.renderer().render(new Brand("text", null)));
        assertThat(sheets).as("sheets, against %d with a text header", asPlainText.size()).hasSize(asPlainText.size());

        boolean hasLogoImage = brand.branding() != null && brand.branding().getLogoImage() != null;
        boolean replaced = brand.mode() == BrandingMode.LETTERHEAD || brand.mode() == BrandingMode.PREPRINTED;

        int brandImages;
        switch (doc.identity()) {
            case HEADER -> {
                brandImages = brand.mode() == BrandingMode.LOGO || brand.mode() == BrandingMode.LETTERHEAD ? 1 : 0;
                // The address is header text, so a letterhead takes one copy of it away. The purchase
                // order keeps a second in its bill-to box, which is then the only place it is typed.
                int addressedAsPlainText = BrandingFixtures.count(asPlainText.get(0).replaceAll("\\s+", " "), "Plot No. 214/B");
                assertThat(BrandingFixtures.count(first, "Plot No. 214/B"))
                        .isEqualTo(replaced ? addressedAsPlainText - 1 : addressedAsPlainText);
                if (!replaced) {
                    assertThat(first).contains(COMPANY).contains("Plot No. 214/B");
                }
            }
            case CHALLAN -> {
                brandImages = brand.mode() == BrandingMode.LOGO || brand.mode() == BrandingMode.LETTERHEAD ? 1 : 0;
                // The consignor box and the signature still name the company; only the header stops doing so.
                int namedAsPlainText = BrandingFixtures.count(asPlainText.get(0).replaceAll("\\s+", " "), COMPANY);
                assertThat(BrandingFixtures.count(first, COMPANY)).isEqualTo(replaced ? namedAsPlainText - 1 : namedAsPlainText);
                assertThat(first).contains(COMPANY).contains("24ABCDE1234F1Z5");
            }
            case SELLER_BOX -> {
                brandImages = brand.mode() == BrandingMode.LOGO || brand.mode() == BrandingMode.LETTERHEAD ? 1 : 0;
                assertThat(first).contains(COMPANY).contains("24ABCDE1234F1Z5");
            }
            default -> {
                brandImages = hasLogoImage && brand.mode() != BrandingMode.NONE ? 1 : 0;
                assertThat(first).contains(COMPANY).doesNotContain("NEXTGEN MANAGER ERP");
            }
        }
        assertThat(imagesOnFirstSheet(pdf)).as("images on the first sheet").isEqualTo(doc.ownImages() + brandImages);
    }

    // ── documents ─────────────────────────────────────────────────────────────

    static byte[] purchaseOrder(Brand brand) {
        ContactAddress address = new ContactAddress();
        address.setStreet1("Shed 9, Aji Industrial Estate");
        address.setCity("Rajkot");
        address.setState("Gujarat");
        address.setPinCode("360003");

        SalesOrder forItsCustomer = InvoicePdfBrandingTest.order(0);

        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderNumber("PO/2026-27/0007");
        po.setOrderDate(new Date());
        po.setExpectedDeliveryDate(new Date());
        po.setVendor(forItsCustomer.getCustomer());
        po.setVendorBillingAddress(address);
        po.setGstTreatment(GstTreatment.INTRA_STATE);
        po.setPaymentTerms("30 days");
        po.setSubtotal(new BigDecimal("12000.00"));
        po.setTaxableValue(new BigDecimal("12000.00"));
        po.setCgstAmount(new BigDecimal("1080.00"));
        po.setSgstAmount(new BigDecimal("1080.00"));
        po.setIgstAmount(BigDecimal.ZERO);
        po.setGrandTotal(new BigDecimal("14160.00"));

        List<PurchaseOrderItem> lines = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            PurchaseOrderItem line = new PurchaseOrderItem();
            line.setDescription("SS 304 Round Bar 25mm");
            line.setHsnCode("7222");
            line.setUom("KG");
            line.setQuantityOrdered(10);
            line.setUnitPrice(new BigDecimal("300.00"));
            line.setTaxableValue(new BigDecimal("3000.00"));
            line.setGstRatePct(new BigDecimal("18"));
            line.setCgstAmount(new BigDecimal("270.00"));
            line.setSgstAmount(new BigDecimal("270.00"));
            line.setLineTotal(new BigDecimal("3540.00"));
            lines.add(line);
        }
        po.setItems(lines);

        return new PurchaseOrderPdfService(engine(), companyRepository(), brandingService(brand.branding())).generate(po);
    }

    static byte[] taxInvoice(Brand brand) {
        SalesOrder so = InvoicePdfBrandingTest.order(0);
        so.getCustomer().setStateCode("24");
        so.setDeliveryNotes(List.of());

        TaxInvoice invoice = new TaxInvoice();
        invoice.setInvoiceNumber("INV/2026-27/0101");
        invoice.setInvoiceDate(LocalDate.of(2026, 5, 20));
        invoice.setSalesOrder(so);
        invoice.setSubTotal(new BigDecimal("20000.00"));
        invoice.setTaxableValue(new BigDecimal("20000.00"));
        invoice.setCgstAmount(new BigDecimal("1800.00"));
        invoice.setSgstAmount(new BigDecimal("1800.00"));
        invoice.setIgstAmount(BigDecimal.ZERO);
        invoice.setTotalPayableAmount(new BigDecimal("23600.00"));

        List<InvoiceItem> items = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            InvoiceItem item = new InvoiceItem();
            item.setInventoryItem(item("Flush Bottom Valve " + i));
            item.setQty(new BigDecimal("2"));
            item.setPricePerUnit(new BigDecimal("5000.00"));
            item.setCgstAmount(new BigDecimal("900.00"));
            item.setSgstAmount(new BigDecimal("900.00"));
            item.setIgstAmount(BigDecimal.ZERO);
            item.setTotalAmount(new BigDecimal("11800.00"));
            items.add(item);
        }
        invoice.setItems(items);

        TaxInvoiceRepository invoices = mock(TaxInvoiceRepository.class);
        when(invoices.findById(anyLong())).thenReturn(Optional.of(invoice));
        return new TaxInvoicePdfService(engine(), invoices, companyRepository(), brandingService(brand.branding()))
                .generatePdf(1L);
    }

    static byte[] deliveryNote(Brand brand) {
        DeliveryNote dn = new DeliveryNote();
        dn.setDeliveryNoteNo("DC/2026-27/0055");
        dn.setDeliveryDate(new Date());
        dn.setSalesOrder(InvoicePdfBrandingTest.order(0));
        dn.setDispatchThrough("By Road");

        List<DeliveryNoteItem> items = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            DeliveryNoteItem item = new DeliveryNoteItem();
            item.setInventoryItem(item("Flush Bottom Valve " + i));
            item.setQuantityDelivered(new BigDecimal("2"));
            item.setInventoryInstanceList(List.of());
            items.add(item);
        }
        dn.setItems(items);

        DeliveryNoteRepository notes = mock(DeliveryNoteRepository.class);
        when(notes.findById(anyLong())).thenReturn(Optional.of(dn));
        return new DeliveryNotePdfService(engine(), notes, companyRepository(), brandingService(brand.branding()))
                .generatePdf(1L);
    }

    static byte[] packingList(Brand brand) {
        PickList pick = new PickList();
        pick.setPickNumber("PICK-0009");

        PackageLine line = new PackageLine();
        line.setInventoryItem(item("Flush Bottom Valve 1"));
        line.setQuantity(new BigDecimal("2"));

        PackageBox box = new PackageBox();
        box.setBoxNumber(1);
        box.setBoxType("Wooden crate");
        box.setLengthCm(new BigDecimal("60"));
        box.setWidthCm(new BigDecimal("40"));
        box.setHeightCm(new BigDecimal("35"));
        box.setGrossWeightKg(new BigDecimal("42.5"));
        box.setNetWeightKg(new BigDecimal("38"));
        box.setLines(List.of(line));

        PackingSlip slip = new PackingSlip();
        slip.setSlipNumber("PKS-0012");
        slip.setSalesOrder(InvoicePdfBrandingTest.order(0));
        slip.setPickList(pick);
        slip.setCreationDate(new Date());
        slip.setBoxes(List.of(box));

        PackingSlipRepository slips = mock(PackingSlipRepository.class);
        when(slips.findLiveById(anyLong())).thenReturn(Optional.of(slip));
        return new PackingSlipPdfService(engine(), slips, companyRepository(), brandingService(brand.branding()))
                .generatePdf(1L);
    }

    /** The quotation builds its own template engine and is laid out by a different PDF library (iText). */
    static byte[] quotation(Brand brand) {
        QuotationProducts product = new QuotationProducts();
        product.setProductNameRequired("Flush Bottom Valve 1");
        product.setQty(new BigDecimal("2"));
        product.setUnitPriceAfterDiscount(new BigDecimal("5000.00"));
        product.setTotalAmountOfProduct(new BigDecimal("10000.00"));

        Quotation quotation = new Quotation();
        quotation.setQtnNo("QTN-0031");
        quotation.setQtnDate(LocalDate.of(2026, 5, 2));
        quotation.setQuotationProducts(List.of(product));
        quotation.setNetAmount(new BigDecimal("10000.00"));
        quotation.setDiscountAmount(BigDecimal.ZERO);
        quotation.setDiscountPercentage(BigDecimal.ZERO);
        quotation.setPackagingAndForwardingCharges(BigDecimal.ZERO);
        quotation.setPackagingAndForwardingChargesPercentage(BigDecimal.ZERO);
        quotation.setGstPercentage(new BigDecimal("18"));
        quotation.setGstAmount(new BigDecimal("1800.00"));
        quotation.setRoundOff(BigDecimal.ZERO);
        quotation.setTotalAmount(new BigDecimal("11800.00"));

        QuotationRepository quotations = mock(QuotationRepository.class);
        when(quotations.findByActiveId(anyLong())).thenReturn(quotation);

        QuotationServiceImp service = new QuotationServiceImp();
        ReflectionTestUtils.setField(service, "quotationRepository", quotations);
        ReflectionTestUtils.setField(service, "companyService", companyDetailsService());
        ReflectionTestUtils.setField(service, "documentBrandingService", brandingService(brand.branding()));
        return service.downloadQuotationPdf(1L).getBody();
    }

    static byte[] jobWorkChallan(Brand brand) throws Exception {
        JobWorkChallan entity = new JobWorkChallan();
        entity.setVendor(InvoicePdfBrandingTest.order(0).getCustomer());

        List<JobWorkChallanDTO.LineDTO> lines = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            JobWorkChallanDTO.LineDTO line = new JobWorkChallanDTO.LineDTO();
            line.setItemCode("FBTM-BODY-" + i);
            line.setItemName("Valve body casting");
            line.setHsnCode("84819090");
            line.setUom("NOS");
            line.setQuantityDispatched(new BigDecimal("20"));
            line.setValuePerUnit(new BigDecimal("850.00"));
            lines.add(line);
        }
        JobWorkChallanDTO dto = new JobWorkChallanDTO();
        dto.setChallanNumber("JWC/2026-27/0003");
        dto.setVendorName("Shreeji Engineering Works");
        dto.setVendorGstNumber("24AAAAA0000A1Z5");
        dto.setStatus(ChallanStatus.DISPATCHED);
        dto.setDispatchDate(new Date());
        dto.setExpectedReturnDate(new Date());
        dto.setDaysRemainingForReturn(30L);
        dto.setLines(lines);

        JobWorkChallanRepository challans = mock(JobWorkChallanRepository.class);
        when(challans.findById(anyLong())).thenReturn(Optional.of(entity));
        JobWorkChallanService challanService = mock(JobWorkChallanService.class);
        when(challanService.getById(anyLong())).thenReturn(dto);

        JobWorkChallanExportServiceImpl service = new JobWorkChallanExportServiceImpl();
        ReflectionTestUtils.setField(service, "challanRepo", challans);
        ReflectionTestUtils.setField(service, "challanService", challanService);
        ReflectionTestUtils.setField(service, "companyDetailsService", companyDetailsService());
        ReflectionTestUtils.setField(service, "documentBrandingService", brandingService(brand.branding()));
        return service.generateChallanPdf(1L);
    }

    private static CompanyDetailsService companyDetailsService() {
        CompanyDetails c = BrandingFixtures.company();
        CompanyDetailsService companies = mock(CompanyDetailsService.class);
        when(companies.get()).thenReturn(new CompanyDetailsDTO(1L, c.getCompanyName(), null, null, c.getGstNumber(),
                c.getPanNumber(), null, c.getCinNumber(), c.getPhone(), c.getEmail(), null, c.getStreet1(),
                c.getStreet2(), c.getCity(), c.getState(), c.getPinCode(), "India", "INR", 4, null,
                null, null, null, null, null, null));
        return companies;
    }

    static byte[] pickList(Brand brand) throws Exception {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNumber("WO-2026-0450");
        wo.setPlannedQuantity(new BigDecimal("25"));

        WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
        when(workOrders.findById(any())).thenReturn(Optional.of(wo));

        WorkOrderExportServiceImpl service = new WorkOrderExportServiceImpl();
        ReflectionTestUtils.setField(service, "workOrderRepository", workOrders);
        ReflectionTestUtils.setField(service, "documentBrandingService", brandingService(brand.branding()));
        return service.generateMaterialPickList(450);
    }

    private static InventoryItem item(String name) {
        InventoryItem item = new InventoryItem();
        item.setName(name);
        item.setHsnCode("84818030");
        item.setUom(UOM.NOS);
        return item;
    }
}
