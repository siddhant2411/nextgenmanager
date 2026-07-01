package com.nextgenmanager.nextgenmanager.accounting.gst.register.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.InwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterDto;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNote;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNoteStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.repository.DebitNoteRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
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
class GstRegisterServiceImplTest {

    @Mock private TaxInvoiceRepository taxInvoiceRepo;
    @Mock private SalesCreditNoteRepository creditNoteRepo;
    @Mock private VendorInvoiceRepository vendorInvoiceRepo;
    @Mock private DebitNoteRepository debitNoteRepo;
    @Mock private GstResolver gstResolver;
    @Mock private CompanyDetailsRepository companyRepo;

    private GstRegisterServiceImpl service;

    private static final LocalDate FROM = LocalDate.of(2025, 5, 1);
    private static final LocalDate TO = LocalDate.of(2025, 5, 31);

    @BeforeEach
    void setUp() {
        GstSupport gstSupport = new GstSupport(gstResolver, companyRepo);
        service = new GstRegisterServiceImpl(taxInvoiceRepo, creditNoteRepo, vendorInvoiceRepo, debitNoteRepo, gstSupport);
        when(gstResolver.resolveCompanyStateCode()).thenReturn("24");          // home state Gujarat
        // Party state = its own stateCode (GstSupport delegates to GstResolver.resolveVendorStateCode).
        lenient().when(gstResolver.resolveVendorStateCode(any(Contact.class)))
                .thenAnswer(inv -> ((Contact) inv.getArgument(0)).getStateCode());
    }

    private Contact party(String gstin, String state) {
        Contact c = new Contact();
        c.setCompanyName("Party " + state);
        c.setGstNumber(gstin);
        c.setStateCode(state);
        return c;
    }

    @Test
    void outwardRegister_includesInvoiceAndCreditNote_intraStateSplit_totalsTie() {
        Contact customer = party("24AAACC0000A1Z5", "24"); // intra-state (same as company)

        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        TaxInvoice inv = new TaxInvoice();
        inv.setInvoiceNumber("INV/2526/0001");
        inv.setInvoiceDate(LocalDate.of(2025, 5, 10));
        inv.setSalesOrder(so);
        inv.setTaxableValue(new BigDecimal("1000.00"));
        inv.setCgstAmount(new BigDecimal("90.00"));
        inv.setSgstAmount(new BigDecimal("90.00"));
        inv.setIgstAmount(BigDecimal.ZERO);
        inv.setTotalPayableAmount(new BigDecimal("1180.00"));

        SalesCreditNote cn = new SalesCreditNote();
        cn.setCreditNoteNumber("CN/2526/0001");
        cn.setCreditNoteDate(LocalDate.of(2025, 5, 20));
        cn.setCustomer(customer);
        cn.setSubtotal(100.00);
        cn.setTotalGstAmount(18.00);
        cn.setTotalAmount(118.00);

        when(taxInvoiceRepo.findForGstRegister(FROM, TO)).thenReturn(List.of(inv));
        when(creditNoteRepo.findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
                SalesCreditNoteStatus.CONFIRMED, FROM, TO)).thenReturn(List.of(cn));

        OutwardRegisterDto reg = service.outwardRegister(FROM, TO);

        assertThat(reg.rows()).hasSize(2);
        assertThat(reg.rows().get(0).docType()).isEqualTo("INV");
        assertThat(reg.rows().get(1).docType()).isEqualTo("CRN");
        // intra-state credit note GST splits 50/50
        assertThat(reg.rows().get(1).cgst()).isEqualByComparingTo("9.00");
        assertThat(reg.rows().get(1).sgst()).isEqualByComparingTo("9.00");
        assertThat(reg.rows().get(1).igst()).isEqualByComparingTo("0");

        assertThat(reg.totals().taxableValue()).isEqualByComparingTo("1100.00");
        assertThat(reg.totals().cgst()).isEqualByComparingTo("99.00");
        assertThat(reg.totals().sgst()).isEqualByComparingTo("99.00");
        assertThat(reg.totals().total()).isEqualByComparingTo("1298.00");
    }

    @Test
    void inwardRegister_taxableIsGrandTotalMinusTaxes_carriesItcFlags_interStateSplit() {
        Contact vendor = party("27BBBCC1111B2Z6", "27"); // inter-state

        VendorInvoice vi = new VendorInvoice();
        vi.setInvoiceNumber("BILL-77");
        vi.setInvoiceDate(LocalDate.of(2025, 5, 12));
        vi.setVendor(vendor);
        vi.setCgstAmount(BigDecimal.ZERO);
        vi.setSgstAmount(BigDecimal.ZERO);
        vi.setIgstAmount(new BigDecimal("180.00"));
        vi.setCessAmount(BigDecimal.ZERO);
        vi.setGrandTotal(new BigDecimal("1180.00"));
        vi.setItcEligible(true);
        vi.setReverseCharge(false);

        DebitNote dn = new DebitNote();
        dn.setDebitNoteNumber("DBN-9");
        dn.setDebitNoteDate(LocalDate.of(2025, 5, 18));
        dn.setVendor(vendor);
        dn.setSubtotal(50.00);
        dn.setTotalGstAmount(9.00);
        dn.setTotalAmount(59.00);

        when(vendorInvoiceRepo.findForGstRegister(FROM, TO)).thenReturn(List.of(vi));
        when(debitNoteRepo.findByStatusAndDebitNoteDateBetweenAndDeletedDateIsNullOrderByDebitNoteDateAscDebitNoteNumberAsc(
                DebitNoteStatus.CONFIRMED, FROM, TO)).thenReturn(List.of(dn));

        InwardRegisterDto reg = service.inwardRegister(FROM, TO);

        assertThat(reg.rows()).hasSize(2);
        // taxable = grandTotal - taxes = 1180 - 180
        assertThat(reg.rows().get(0).taxableValue()).isEqualByComparingTo("1000.00");
        assertThat(reg.rows().get(0).itcEligible()).isTrue();
        assertThat(reg.rows().get(0).reverseCharge()).isFalse();
        // inter-state debit note -> IGST only
        assertThat(reg.rows().get(1).igst()).isEqualByComparingTo("9.00");
        assertThat(reg.rows().get(1).cgst()).isEqualByComparingTo("0");

        assertThat(reg.totals().taxableValue()).isEqualByComparingTo("1050.00");
        assertThat(reg.totals().igst()).isEqualByComparingTo("189.00");
        assertThat(reg.totals().total()).isEqualByComparingTo("1239.00");
    }
}
