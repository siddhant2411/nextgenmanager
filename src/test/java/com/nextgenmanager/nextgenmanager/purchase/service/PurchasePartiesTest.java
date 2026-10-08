package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.company.BrandingFixtures;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import com.nextgenmanager.nextgenmanager.purchase.model.ShipToKind;
import com.nextgenmanager.nextgenmanager.sales.dto.DocumentParty;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasePartiesTest {

    @Test
    void anOrderThatSaysNothingIsDeliveredToTheRegisteredAddress() {
        PurchaseOrder po = new PurchaseOrder();

        DocumentParty billTo = PurchaseParties.billTo(company());
        DocumentParty shipTo = PurchaseParties.shipTo(po, company());

        assertThat(PurchaseParties.kindOf(po)).isEqualTo(ShipToKind.COMPANY);
        assertThat(billTo.getName()).isEqualTo("Vajra Auto Components Private Limited");
        assertThat(billTo.getAddress()).isEqualTo("Plot 214/B, GIDC Metoda, Rajkot, Gujarat - 360021");
        assertThat(billTo.getStateLabel()).isEqualTo("Gujarat (24)");
        assertThat(shipTo.getName()).isEqualTo(billTo.getName());
        assertThat(shipTo.getAddress()).isEqualTo(billTo.getAddress());
        assertThat(shipTo.getGstin()).isEqualTo("24ABCDE1234F1Z5");
    }

    @Test
    void aPlantIsStillTheCompanyAtAnotherAddress() {
        PurchaseOrder po = new PurchaseOrder();
        po.setShipToWarehouse(plant("Chakan Plant", "Maharashtra", null));

        DocumentParty shipTo = PurchaseParties.shipTo(po, company());

        assertThat(PurchaseParties.kindOf(po)).isEqualTo(ShipToKind.PLANT);
        assertThat(shipTo.getName()).isEqualTo("Vajra Auto Components Private Limited - Chakan Plant");
        assertThat(shipTo.getAddress()).isEqualTo("Plot 7, MIDC Chakan, Pune, Maharashtra - 410501");
        // No registration of its own, so the company GSTIN; the state is where the plant stands.
        assertThat(shipTo.getGstin()).isEqualTo("24ABCDE1234F1Z5");
        assertThat(shipTo.getStateLabel()).isEqualTo("Maharashtra (27)");
        // The vendor still invoices the registered office.
        assertThat(PurchaseParties.billTo(company()).getStateCode()).isEqualTo("24");
    }

    @Test
    void aSeparatelyRegisteredPlantPrintsItsOwnGstin() {
        PurchaseOrder po = new PurchaseOrder();
        po.setShipToWarehouse(plant("Chakan Plant", "Maharashtra", "27ABCDE1234F1Z9"));

        DocumentParty shipTo = PurchaseParties.shipTo(po, company());

        assertThat(shipTo.getGstin()).isEqualTo("27ABCDE1234F1Z9");
        assertThat(shipTo.getStateCode()).isEqualTo("27");
    }

    @Test
    void aDirectDeliveryNamesTheCustomerAndNeverTheCompanyGstin() {
        PurchaseOrder po = new PurchaseOrder();
        po.setShipToAddress(customerSite("29AAACB1111B1Z2"));

        DocumentParty shipTo = PurchaseParties.shipTo(po, company());

        assertThat(PurchaseParties.kindOf(po)).isEqualTo(ShipToKind.PARTY);
        assertThat(shipTo.getName()).isEqualTo("Bharat Earth Movers");
        assertThat(shipTo.getAddress()).isEqualTo("Gate 3, Peenya Industrial Area, Bengaluru, Karnataka - 560058");
        assertThat(shipTo.getGstin()).isEqualTo("29AAACB1111B1Z2");
        assertThat(shipTo.getStateLabel()).isEqualTo("Karnataka (29)");

        po.setShipToAddress(customerSite(null));
        shipTo = PurchaseParties.shipTo(po, company());
        assertThat(shipTo.getGstin()).isNull();
        assertThat(shipTo.getStateLabel()).isEqualTo("Karnataka (29)");
    }

    /** The printed order used to carry no delivery address at all. */
    @Test
    void thePrintedOrderSaysWhoIsInvoicedAndWhereToDeliver() throws Exception {
        PurchaseOrder toPlant = order();
        toPlant.setShipToWarehouse(plant("Chakan Plant", "Maharashtra", null));
        String plantSheet = render(toPlant);
        assertThat(plantSheet).contains("Bill To (Invoice To):").contains("Ship To (Deliver To):");
        assertThat(plantSheet).contains("Chakan Plant").contains("Plot 7, MIDC Chakan").contains("State: Maharashtra (27)");

        PurchaseOrder direct = order();
        direct.setShipToAddress(customerSite("29AAACB1111B1Z2"));
        String directSheet = render(direct);
        assertThat(directSheet).contains("Ship To (Deliver Directly To):");
        assertThat(directSheet).contains("Bharat Earth Movers").contains("Peenya Industrial Area").contains("GSTIN: 29AAACB1111B1Z2");

        assertThat(render(order())).contains("Ship To (Deliver To):");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static String render(PurchaseOrder po) throws Exception {
        byte[] pdf = new PurchaseOrderPdfService(BrandingFixtures.engine(), BrandingFixtures.companyRepository(),
                BrandingFixtures.brandingService(null)).generate(po);
        BrandingFixtures.sample("purchase order ship to " + PurchaseParties.kindOf(po),
                new BrandingFixtures.Brand("text", null), pdf);
        return BrandingFixtures.sheets(pdf).get(0).replaceAll("\\s+", " ");
    }

    private static PurchaseOrder order() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Pune Castings & Forgings Pvt Ltd");
        vendor.setGstNumber("27AAFCP3344L1Z2");

        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setDescription("Brake Disc Casting FG260 - 260mm");
        line.setHsnCode("7325");
        line.setUom("NOS");
        line.setQuantityOrdered(600);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setTaxableValue(new BigDecimal("60000.00"));
        line.setGstRatePct(new BigDecimal("18"));
        line.setCgstAmount(BigDecimal.ZERO);
        line.setSgstAmount(BigDecimal.ZERO);
        line.setIgstAmount(new BigDecimal("10800.00"));
        line.setLineTotal(new BigDecimal("70800.00"));

        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderNumber("PO/2026-27/0009");
        po.setOrderDate(new Date());
        po.setVendor(vendor);
        po.setGrandTotal(new BigDecimal("70800.00"));
        po.setItems(List.of(line));
        return po;
    }

    private static CompanyDetails company() {
        CompanyDetails c = new CompanyDetails();
        c.setCompanyName("Vajra Auto Components Private Limited");
        c.setStreet1("Plot 214/B");
        c.setStreet2("GIDC Metoda");
        c.setCity("Rajkot");
        c.setState("Gujarat");
        c.setPinCode("360021");
        c.setGstNumber("24ABCDE1234F1Z5");
        return c;
    }

    private static Warehouse plant(String name, String state, String gstin) {
        Warehouse w = new Warehouse();
        w.setCode("CHK");
        w.setName(name);
        w.setAddressLine1("Plot 7, MIDC Chakan");
        w.setCity("Pune");
        w.setState(state);
        w.setPincode("410501");
        w.setGstin(gstin);
        return w;
    }

    private static ContactAddress customerSite(String gstin) {
        Contact customer = new Contact();
        customer.setCompanyName("Bharat Earth Movers");
        customer.setGstNumber(gstin);
        ContactAddress a = new ContactAddress();
        a.setStreet1("Gate 3, Peenya Industrial Area");
        a.setCity("Bengaluru");
        a.setState("Karnataka");
        a.setPinCode("560058");
        a.setContact(customer);
        return a;
    }
}
