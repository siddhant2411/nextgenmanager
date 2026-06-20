package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto.Gstr1Dto;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.service.HsnSummaryService;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesCreditNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Gstr1ServiceImplTest {

    @Mock private TaxInvoiceRepository taxInvoiceRepo;
    @Mock private SalesCreditNoteRepository creditNoteRepo;
    @Mock private HsnSummaryService hsnSummaryService;
    @Mock private Gstr1JsonWriter jsonWriter;
    @Mock private GstResolver gstResolver;
    @Mock private CompanyDetailsRepository companyRepo;

    private Gstr1ServiceImpl service;

    private static final LocalDate FROM = LocalDate.of(2025, 5, 1);
    private static final LocalDate TO = LocalDate.of(2025, 5, 31);

    @BeforeEach
    void setUp() {
        GstSupport gstSupport = new GstSupport(gstResolver, companyRepo);
        service = new Gstr1ServiceImpl(taxInvoiceRepo, creditNoteRepo, hsnSummaryService, jsonWriter, gstSupport);
        when(gstResolver.resolveCompanyStateCode()).thenReturn("24");
        lenient().when(companyRepo.findAll()).thenReturn(List.of());
        lenient().when(gstResolver.resolveVendorStateCode(any(Contact.class)))
                .thenAnswer(inv -> ((Contact) inv.getArgument(0)).getStateCode());
        lenient().when(hsnSummaryService.summary(FROM, TO))
                .thenReturn(new HsnSummaryDto(FROM, TO, List.of(), null));
        lenient().when(creditNoteRepo.findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
                SalesCreditNoteStatus.CONFIRMED, FROM, TO)).thenReturn(List.of());
    }

    private Contact party(String gstin, String state) {
        Contact c = new Contact();
        c.setCompanyName("Party-" + state);
        c.setGstNumber(gstin);
        c.setStateCode(state);
        return c;
    }

    private TaxInvoice invoice(String no, Contact customer, String taxable, String cgst, String sgst, String igst) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        TaxInvoice inv = new TaxInvoice();
        inv.setInvoiceNumber(no);
        inv.setInvoiceDate(LocalDate.of(2025, 5, 10));
        inv.setSalesOrder(so);
        BigDecimal t = new BigDecimal(taxable), c = new BigDecimal(cgst), s = new BigDecimal(sgst), i = new BigDecimal(igst);
        BigDecimal total = t.add(c).add(s).add(i);
        inv.setTaxableValue(t);
        inv.setCgstAmount(c);
        inv.setSgstAmount(s);
        inv.setIgstAmount(i);
        inv.setTotalPayableAmount(total);

        InvoiceItem item = new InvoiceItem();
        item.setCgstAmount(c);
        item.setSgstAmount(s);
        item.setIgstAmount(i);
        item.setTotalAmount(total);   // totalAmount = taxable + tax
        inv.getItems().add(item);
        return inv;
    }

    @Test
    void classifiesByGstinAndThreshold_b2b_b2cs_b2cl() {
        Contact registered = party("24AAACC0000A1Z5", "24");      // B2B intra
        Contact b2cSmall = party(null, "24");                      // B2CS intra
        Contact b2cBig = party(null, "27");                        // B2CL inter, big value

        when(taxInvoiceRepo.findForGstRegister(FROM, TO)).thenReturn(List.of(
                invoice("INV-1", registered, "1000.00", "90.00", "90.00", "0.00"),
                invoice("INV-2", b2cSmall, "500.00", "45.00", "45.00", "0.00"),
                invoice("INV-3", b2cBig, "300000.00", "0.00", "0.00", "54000.00")
        ));

        Gstr1Dto g = service.build(FROM, TO);

        assertThat(g.b2b()).hasSize(1);
        assertThat(g.b2b().get(0).ctin()).isEqualTo("24AAACC0000A1Z5");
        assertThat(g.b2b().get(0).items()).hasSize(1);
        assertThat(g.b2b().get(0).items().get(0).rate()).isEqualByComparingTo("18");
        assertThat(g.b2b().get(0).items().get(0).taxableValue()).isEqualByComparingTo("1000.00");

        assertThat(g.b2cs()).hasSize(1);
        assertThat(g.b2cs().get(0).placeOfSupply()).isEqualTo("24");
        assertThat(g.b2cs().get(0).taxableValue()).isEqualByComparingTo("500.00");

        assertThat(g.b2cl()).hasSize(1);
        assertThat(g.b2cl().get(0).invoiceValue()).isEqualByComparingTo("354000.00");

        // DOCS series spans the invoice numbers
        assertThat(g.docs()).anyMatch(d -> d.nature().equals("Tax Invoices") && d.totalCount() == 3);
    }

    @Test
    void b2cInterStateBelowThreshold_goesToB2cs_notB2cl() {
        Contact b2cMid = party(null, "27");   // inter-state but below 2.5L
        when(taxInvoiceRepo.findForGstRegister(FROM, TO)).thenReturn(List.of(
                invoice("INV-9", b2cMid, "1000.00", "0.00", "0.00", "180.00")
        ));

        Gstr1Dto g = service.build(FROM, TO);

        assertThat(g.b2cl()).isEmpty();
        assertThat(g.b2cs()).hasSize(1);
        assertThat(g.b2cs().get(0).igst()).isEqualByComparingTo("180.00");
    }

    @Test
    void creditNoteToRegisteredParty_goesToCdnr_andNetsTotals() {
        Contact registered = party("24AAACC0000A1Z5", "24");
        when(taxInvoiceRepo.findForGstRegister(FROM, TO)).thenReturn(List.of(
                invoice("INV-1", registered, "1000.00", "90.00", "90.00", "0.00")
        ));

        SalesCreditNote cn = new SalesCreditNote();
        cn.setCreditNoteNumber("CN-1");
        cn.setCreditNoteDate(LocalDate.of(2025, 5, 20));
        cn.setCustomer(registered);
        cn.setTotalAmount(118.00);
        SalesCreditNoteItem cnItem = new SalesCreditNoteItem();
        cnItem.setReturnedQty(1);
        cnItem.setRate(100.00);
        cnItem.setGstRate(18);
        cnItem.setGstAmount(18.00);
        cn.getItems().add(cnItem);

        when(creditNoteRepo.findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
                SalesCreditNoteStatus.CONFIRMED, FROM, TO)).thenReturn(List.of(cn));

        Gstr1Dto g = service.build(FROM, TO);

        assertThat(g.cdnr()).hasSize(1);
        assertThat(g.cdnur()).isEmpty();
        assertThat(g.cdnr().get(0).noteNo()).isEqualTo("CN-1");
        // intra split: 9 CGST + 9 SGST
        assertThat(g.cdnr().get(0).items().get(0).cgst()).isEqualByComparingTo("9.00");
        // totals net the credit note out of the invoice: taxable 1000 - 100 = 900
        assertThat(g.totals().taxableValue()).isEqualByComparingTo("900.00");
        assertThat(g.totals().cgst()).isEqualByComparingTo("81.00");
    }
}
