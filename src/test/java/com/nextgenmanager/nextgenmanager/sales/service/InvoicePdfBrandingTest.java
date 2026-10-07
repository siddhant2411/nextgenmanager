package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.company.BrandingFixtures;
import com.nextgenmanager.nextgenmanager.company.BrandingFixtures.Brand;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.model.DocumentBranding;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesPayment;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.branding;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.count;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.image;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.sheets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The invoice, proforma invoice and order acknowledgement draw every page as one fixed-height box,
 * so a header that grows by a few millimetres used to push the signature onto a sheet of its own.
 * These tests hold the line on that: whatever the branding mode, every sheet of the PDF is a whole
 * page of the document, and no item goes missing.
 */
public class InvoicePdfBrandingTest {

    record Doc(String name, String title, boolean listsPayments, Function<InvoicePdfService, byte[]> render) {
        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Doc> DOCS = List.of(
            new Doc("invoice", "TAX INVOICE", true, s -> s.generateInvoicePdf(1L)),
            new Doc("proforma", "PRO FORMA INVOICE", false, s -> s.generateProformaInvoicePdf(1L)),
            new Doc("order-ack", "ORDER ACKNOWLEDGEMENT", false, s -> s.generateOrderAcknowledgementPdf(1L)));

    static List<Brand> brands() {
        return BrandingFixtures.brands();
    }

    static Stream<Arguments> everyCombination() {
        List<Arguments> all = new ArrayList<>();
        for (Brand brand : brands()) {
            for (Doc doc : DOCS) {
                for (int items : new int[]{1, 10, 11, 21}) {
                    for (int payments : doc.listsPayments() ? new int[]{0, 3} : new int[]{0}) {
                        all.add(Arguments.of(brand, doc, items, payments));
                    }
                }
            }
        }
        return all.stream();
    }

    @ParameterizedTest(name = "{0} | {1} | {2} items | {3} payments")
    @MethodSource("everyCombination")
    void everySheetIsAWholePageAndNoItemIsLost(Brand brand, Doc doc, int items, int payments) throws Exception {
        byte[] pdf = doc.render().apply(service(brand.branding(), items, payments));
        if (items == 10 && payments == 0) BrandingFixtures.sample(doc.name(), brand, pdf);
        List<String> sheets = sheets(pdf);

        // A sheet without the document title is the tail of a page that did not fit.
        assertThat(sheets).allSatisfy(sheet -> assertThat(sheet).contains(doc.title()));

        String whole = String.join(" ", sheets);
        assertThat(count(whole, "FBTM-")).isEqualTo(items);
        assertThat(whole).contains("Authorised Signatory");
    }

    /** The logo sits inside the header row that is already there, so it must not cost a row. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("brands")
    void textAndLogoHeadersStillTakeTenItemsOnOnePage(Brand brand) throws Exception {
        if (brand.mode() != BrandingMode.NONE && brand.mode() != BrandingMode.LOGO) return;

        for (Doc doc : DOCS) {
            if (doc.listsPayments()) continue;
            assertThat(sheets(doc.render().apply(service(brand.branding(), 10, 0)))).as(doc.name()).hasSize(1);
        }
    }

    @Test
    void letterheadReplacesTheTextHeaderButKeepsTheGstin() throws Exception {
        DocumentBranding letterhead = branding(BrandingMode.LETTERHEAD, null, image(2000, 353));
        String withLetterhead = sheets(service(letterhead, 3, 0).generateProformaInvoicePdf(1L)).get(0);
        String withText = sheets(service(null, 3, 0).generateProformaInvoicePdf(1L)).get(0);

        assertThat(withText).contains("Plot No. 214/B");
        assertThat(withLetterhead).doesNotContain("Plot No. 214/B");
        assertThat(withLetterhead).contains("GSTIN: 24ABCDE1234F1Z5").contains("PAN: ABCDE1234F");
    }

    @Test
    void aModeWhoseImageIsMissingPrintsAsText() throws Exception {
        DocumentBranding noImage = branding(BrandingMode.LETTERHEAD, null, null);
        assertThat(sheets(service(noImage, 3, 0).generateProformaInvoicePdf(1L)).get(0)).contains("Plot No. 214/B");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    static InvoicePdfService service(DocumentBranding branding, int itemCount, int paymentCount) {
        return service(branding, itemCount, paymentCount, so -> { });
    }

    static InvoicePdfService service(DocumentBranding branding, int itemCount, int paymentCount, Consumer<SalesOrder> tweak) {
        SalesOrder order = order(itemCount);
        tweak.accept(order);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesPaymentRepository payments = mock(SalesPaymentRepository.class);

        List<SalesPayment> paid = new ArrayList<>();
        for (int i = 0; i < paymentCount; i++) {
            SalesPayment p = new SalesPayment();
            p.setPaymentDate(LocalDate.of(2026, 5, 1 + i));
            p.setPaymentMode(PaymentMode.NEFT);
            p.setReferenceNumber("UTR00012345" + i);
            p.setAmount(new BigDecimal("25000.00"));
            paid.add(p);
        }

        when(orders.findById(anyLong())).thenReturn(Optional.of(order));
        when(payments.findBySalesOrderIdOrderByPaymentDateAsc(anyLong())).thenReturn(paid);
        when(payments.sumAmountBySalesOrderId(anyLong()))
                .thenReturn(new BigDecimal("25000.00").multiply(BigDecimal.valueOf(paymentCount)));

        return new InvoicePdfService(BrandingFixtures.engine(), orders, payments,
                BrandingFixtures.companyRepository(), BrandingFixtures.brandingService(branding));
    }

    public static SalesOrder order(int itemCount) {
        Contact customer = new Contact();
        customer.setCompanyName("Shreeji Engineering Works");
        customer.setGstNumber("24AAAAA0000A1Z5");
        ContactAddress address = new ContactAddress();
        address.setDefault(true);
        address.setStreet1("Survey No. 45, Near Old Octroi Naka");
        address.setStreet2("Gondal Road");
        address.setCity("Rajkot");
        address.setState("Gujarat");
        address.setPinCode("360004");
        customer.setAddresses(List.of(address));

        SalesOrder so = new SalesOrder();
        so.setId(1L);
        so.setOrderNumber("SO/2026-27/0042");
        so.setOrderDate(LocalDate.of(2026, 5, 4));
        so.setCustomer(customer);
        so.setDeliveryAddress("Survey No. 45, Near Old Octroi Naka, Gondal Road, Rajkot, Gujarat - 360004");
        so.setPoNumber("PO-7781");
        so.setPoDate(LocalDate.of(2026, 4, 28));
        so.setPaymentTerms("30% advance, balance against delivery");
        so.setDeliveryDate(LocalDate.of(2026, 6, 1));
        so.setDispatchThrough("By Road");
        so.setTransportMode("Truck");
        so.setIncoterms("Ex-Works");
        so.setReference("Enquiry ENQ-0193");
        so.setRemarks("Material test certificates to accompany the consignment.");
        so.setTaxType(TaxType.CGST_SGST);
        so.setSubTotal(new BigDecimal("100000.00"));
        so.setDiscountPercentage(new BigDecimal("5"));
        so.setDiscountAmount(new BigDecimal("5000.00"));
        so.setFreightAndForwardingCharges(new BigDecimal("1500.00"));
        so.setTaxableValue(new BigDecimal("96500.00"));
        so.setCgstAmount(new BigDecimal("8685.00"));
        so.setSgstAmount(new BigDecimal("8685.00"));
        so.setIgstAmount(BigDecimal.ZERO);
        so.setRoundOffAmount(BigDecimal.ZERO);
        so.setTotalPayableAmount(new BigDecimal("113870.00"));

        List<SalesOrderItem> items = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            InventoryItem item = new InventoryItem();
            item.setName("Flush Bottom Valve " + i);
            item.setItemCode("FBTM-" + (1100 + i));
            item.setUom(UOM.NOS);
            SalesOrderItem line = new SalesOrderItem();
            line.setInventoryItem(item);
            line.setHsnCode("84818030");
            line.setQty(new BigDecimal("2"));
            line.setPricePerUnit(new BigDecimal("5000.00"));
            line.setDiscountPercentage(BigDecimal.ZERO);
            line.setTotalAmountOfProduct(new BigDecimal("10000.00"));
            items.add(line);
        }
        so.setItems(items);
        return so;
    }
}
