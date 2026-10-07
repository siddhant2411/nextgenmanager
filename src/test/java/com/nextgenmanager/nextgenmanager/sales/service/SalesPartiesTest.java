package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.contact.model.AddressType;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.sales.dto.DocumentParty;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.sheets;
import static org.assertj.core.api.Assertions.assertThat;

public class SalesPartiesTest {

    private static final String BILLING = "12 Ring Road, Rajkot, Gujarat - 360004";
    private static final String PLANT = "Plot 7, MIDC Chakan, Pune, Maharashtra - 410501";

    @Test
    void anOrderThatSaysNothingBillsAndShipsToTheCustomersBillingAddress() {
        SalesOrder so = order(customer(
                address(AddressType.SHIPPING, true, "Plot 7, MIDC Chakan", "Pune", "Maharashtra", "410501"),
                address(AddressType.BILLING, false, "12 Ring Road", "Rajkot", "Gujarat", "360004")));

        DocumentParty billTo = SalesParties.billTo(so);
        DocumentParty shipTo = SalesParties.shipTo(so);

        // The shipping address is the default one, and it is still not where the bill goes.
        assertThat(billTo.getAddress()).isEqualTo(BILLING);
        assertThat(billTo.getName()).isEqualTo("Shreeji Engineering Works");
        assertThat(billTo.getGstin()).isEqualTo("24AAAAA0000A1Z5");
        assertThat(billTo.getStateLabel()).isEqualTo("Gujarat (24)");

        assertThat(shipTo.getName()).isEqualTo(billTo.getName());
        assertThat(shipTo.getAddress()).isEqualTo(BILLING);
        assertThat(shipTo.getGstin()).isEqualTo("24AAAAA0000A1Z5");
        assertThat(shipTo.getStateCode()).isEqualTo("24");
    }

    @Test
    void aCustomerWithNoBillingAddressIsBilledAtTheDefaultOne() {
        SalesOrder so = order(customer(
                address(AddressType.FACTORY, false, "Shed 2", "Morbi", "Gujarat", "363641"),
                address(AddressType.SHIPPING, true, "Plot 7, MIDC Chakan", "Pune", "Maharashtra", "410501")));

        assertThat(SalesParties.billTo(so).getAddress()).isEqualTo(PLANT);
    }

    @Test
    void missingAddressPartsAreLeftOutRatherThanPrintedAsNull() {
        SalesOrder so = order(customer(address(AddressType.BILLING, true, "12 Ring Road", null, " ", null)));

        assertThat(SalesParties.billTo(so).getAddress()).isEqualTo("12 Ring Road");
    }

    @Test
    void theOrdersOwnBillingAddressWinsOverTheMaster() {
        SalesOrder so = order(customer(address(AddressType.BILLING, true, "12 Ring Road", "Rajkot", "Gujarat", "360004")));
        so.setBillToAddress("Accounts Dept, 4th Floor, Nariman Point, Mumbai - 400021");

        assertThat(SalesParties.billTo(so).getAddress()).isEqualTo("Accounts Dept, 4th Floor, Nariman Point, Mumbai - 400021");
    }

    @Test
    void shippingToTheCustomersOtherSiteKeepsTheirNameAndGstinButNotTheirState() {
        SalesOrder so = order(customer(address(AddressType.BILLING, true, "12 Ring Road", "Rajkot", "Gujarat", "360004")));
        so.setDeliveryAddress(PLANT);

        DocumentParty shipTo = SalesParties.shipTo(so);

        assertThat(shipTo.getName()).isEqualTo("Shreeji Engineering Works");
        assertThat(shipTo.getAddress()).isEqualTo(PLANT);
        assertThat(shipTo.getGstin()).isEqualTo("24AAAAA0000A1Z5");
        // Nobody said which state the plant is in, so the bill-to state is not passed off as it.
        assertThat(shipTo.getStateLabel()).isNull();

        so.setShipToStateCode("27");
        assertThat(SalesParties.shipTo(so).getStateLabel()).isEqualTo("Maharashtra (27)");
    }

    @Test
    void aThirdPartyConsigneeNeverInheritsTheCustomersGstin() {
        SalesOrder so = order(customer(address(AddressType.BILLING, true, "12 Ring Road", "Rajkot", "Gujarat", "360004")));
        so.setShipToName("Bharat Forge Ltd");
        so.setDeliveryAddress(PLANT);

        DocumentParty shipTo = SalesParties.shipTo(so);
        assertThat(shipTo.getName()).isEqualTo("Bharat Forge Ltd");
        assertThat(shipTo.getGstin()).isNull();
        assertThat(shipTo.getStateLabel()).isNull();

        so.setShipToGstin("27BBBBB1111B1Z2");
        shipTo = SalesParties.shipTo(so);
        assertThat(shipTo.getGstin()).isEqualTo("27BBBBB1111B1Z2");
        assertThat(shipTo.getStateLabel()).isEqualTo("Maharashtra (27)");

        // The bill-to is untouched: tax follows the customer, not the consignee.
        assertThat(SalesParties.billTo(so).getStateCode()).isEqualTo("24");
    }

    @Test
    void anIssuedInvoiceDoesNotFollowLaterChangesToTheOrderOrTheCustomer() {
        Contact customer = customer(address(AddressType.BILLING, true, "12 Ring Road", "Rajkot", "Gujarat", "360004"));
        SalesOrder so = order(customer);
        so.setShipToName("Bharat Forge Ltd");
        so.setShipToGstin("27BBBBB1111B1Z2");
        so.setDeliveryAddress(PLANT);

        TaxInvoice invoice = new TaxInvoice();
        invoice.setSalesOrder(so);
        SalesParties.freezeOnto(invoice, so);

        customer.setCompanyName("Shreeji Engineering Pvt Ltd");
        customer.getAddresses().get(0).setCity("Jamnagar");
        so.setShipToName(null);
        so.setShipToGstin(null);
        so.setDeliveryAddress(null);

        assertThat(SalesParties.billTo(invoice).getName()).isEqualTo("Shreeji Engineering Works");
        assertThat(SalesParties.billTo(invoice).getAddress()).isEqualTo(BILLING);
        assertThat(SalesParties.shipTo(invoice).getName()).isEqualTo("Bharat Forge Ltd");
        assertThat(SalesParties.shipTo(invoice).getAddress()).isEqualTo(PLANT);
        assertThat(SalesParties.shipTo(invoice).getStateLabel()).isEqualTo("Maharashtra (27)");
    }

    @Test
    void anInvoiceRaisedBeforeItKeptACopyReadsFromItsOrder() {
        SalesOrder so = order(customer(address(AddressType.BILLING, true, "12 Ring Road", "Rajkot", "Gujarat", "360004")));
        so.setDeliveryAddress(PLANT);
        TaxInvoice invoice = new TaxInvoice();
        invoice.setSalesOrder(so);

        assertThat(SalesParties.billTo(invoice).getAddress()).isEqualTo(BILLING);
        assertThat(SalesParties.shipTo(invoice).getAddress()).isEqualTo(PLANT);
    }

    /** The printed proforma carries two different parties, each with its own GSTIN and state. */
    @Test
    void theProformaPrintsBothParties() throws Exception {
        String sheet = String.join(" ", sheets(InvoicePdfBrandingTest.service(null, 3, 0, so -> {
            so.setShipToName("Bharat Forge Ltd");
            so.setShipToGstin("27BBBBB1111B1Z2");
            so.setDeliveryAddress(PLANT);
        }).generateProformaInvoicePdf(1L)));

        assertThat(sheet).contains("Shreeji Engineering Works").contains("GSTIN: 24AAAAA0000A1Z5").contains("State: Gujarat (24)");
        assertThat(sheet).contains("Bharat Forge Ltd").contains("GSTIN: 27BBBBB1111B1Z2").contains("State: Maharashtra (27)");
        assertThat(sheet).contains("MIDC Chakan");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static SalesOrder order(Contact customer) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        return so;
    }

    private static Contact customer(ContactAddress... addresses) {
        Contact c = new Contact();
        c.setCompanyName("Shreeji Engineering Works");
        c.setGstNumber("24AAAAA0000A1Z5");
        c.setAddresses(new ArrayList<>(List.of(addresses)));
        return c;
    }

    private static ContactAddress address(AddressType type, boolean isDefault, String street, String city, String state, String pin) {
        ContactAddress a = new ContactAddress();
        a.setAddressType(type);
        a.setDefault(isDefault);
        a.setStreet1(street);
        a.setCity(city);
        a.setState(state);
        a.setPinCode(pin);
        return a;
    }
}
