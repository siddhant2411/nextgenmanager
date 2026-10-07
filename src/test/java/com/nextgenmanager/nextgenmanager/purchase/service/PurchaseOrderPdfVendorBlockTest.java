package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.company.BrandingFixtures;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vendor block of the purchase order. A vendor has to be able to tell from the page alone who
 * the order is for and where, so the block must not depend on an address having been picked on the
 * order: it falls back to what the vendor master holds.
 */
class PurchaseOrderPdfVendorBlockTest {

    private static String render(PurchaseOrder po) throws Exception {
        byte[] pdf = new PurchaseOrderPdfService(BrandingFixtures.engine(), BrandingFixtures.companyRepository(),
                BrandingFixtures.brandingService(null)).generate(po);
        BrandingFixtures.sample("purchase order vendor block " + po.getVendor().getCompanyName(),
                new BrandingFixtures.Brand("text", null), pdf);
        return BrandingFixtures.sheets(pdf).get(0).replaceAll("\\s+", " ");
    }

    private static ContactAddress address(String street1, boolean isDefault) {
        ContactAddress a = new ContactAddress();
        a.setStreet1(street1);
        a.setStreet2("MIDC Bhosari");
        a.setCity("Pune");
        a.setState("Maharashtra");
        a.setPinCode("411026");
        a.setDefault(isDefault);
        return a;
    }

    private static Contact vendor() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Pune Castings & Forgings Pvt Ltd");
        vendor.setTradeName("");
        vendor.setGstNumber("27AAFCP3344L1Z2");
        vendor.setPanNumber("AAFCP3344L");
        vendor.setStateCode("27");
        vendor.setPhone("020-27120000");
        vendor.setEmail("sales@punecastings.example");
        vendor.setMsmeRegistered(true);
        vendor.setMsmeNumber("UDYAM-MH-26-0001234");
        vendor.setAddresses(List.of(address("Gat No. 12, Old Shed", false), address("Plot 88, J Block", true)));

        ContactPersonDetail other = new ContactPersonDetail();
        other.setPersonName("Accounts Desk");
        ContactPersonDetail primary = new ContactPersonDetail();
        primary.setPersonName("Mahesh Kulkarni");
        primary.setPhoneNumber("98220 00000");
        primary.setPrimary(true);
        vendor.setPersonDetails(List.of(other, primary));
        return vendor;
    }

    private static PurchaseOrder order(Contact vendor) {
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setDescription("Brake Disc Casting FG260 - 260mm");
        line.setHsnCode("7325");
        line.setUom("NOS");
        line.setQuantityOrdered(600);

        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderNumber("PO/2026-27/0001");
        po.setOrderDate(new Date());
        po.setVendor(vendor);
        po.setGstTreatment(GstTreatment.INTRA_STATE);
        po.setGrandTotal(new BigDecimal("438960.00"));
        po.setItems(List.of(line));
        return po;
    }

    @Test
    void anOrderWithNoAddressPickedPrintsTheVendorsDefaultAddress() throws Exception {
        String page = render(order(vendor()));

        assertThat(page).contains("Plot 88, J Block").contains("MIDC Bhosari").contains("Pune - 411026")
                .contains("Maharashtra (State Code: 27)");
        assertThat(page).doesNotContain("Gat No. 12, Old Shed");
    }

    @Test
    void theAddressPickedOnTheOrderWinsOverTheDefault() throws Exception {
        PurchaseOrder po = order(vendor());
        po.setVendorBillingAddress(address("Unit 4, Chakan Phase II", false));

        String page = render(po);
        assertThat(page).contains("Unit 4, Chakan Phase II").doesNotContain("Plot 88, J Block");
    }

    @Test
    void theVendorsTaxAndContactDetailsArePrinted() throws Exception {
        String page = render(order(vendor()));

        assertThat(page).contains("GSTIN: 27AAFCP3344L1Z2").contains("PAN: AAFCP3344L")
                .contains("MSME (Udyam): UDYAM-MH-26-0001234")
                .contains("Ph: 020-27120000").contains("Email: sales@punecastings.example")
                .contains("Kind Attn: Mahesh Kulkarni (98220 00000)");
        // An empty trade name used to print as "()".
        assertThat(page).doesNotContain("()");
    }

    @Test
    void aVendorWithNothingOnFileStillPrintsCleanly() throws Exception {
        Contact bare = new Contact();
        bare.setCompanyName("Walk-in Supplier");

        String page = render(order(bare));
        assertThat(page).contains("Walk-in Supplier").contains("Unregistered");
        // The only PAN on the page is the company's own, in the header.
        assertThat(BrandingFixtures.count(page, "PAN:")).isEqualTo(1);
        assertThat(page).doesNotContain("Kind Attn:").doesNotContain("Email:").doesNotContain("null");
    }
}
