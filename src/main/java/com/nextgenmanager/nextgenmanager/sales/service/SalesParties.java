package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.contact.model.AddressType;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import com.nextgenmanager.nextgenmanager.sales.dto.DocumentParty;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who a sales document is billed to and who it is shipped to.
 *
 * <p>Every sales document — order acknowledgement, proforma, invoice, delivery challan, packing
 * list — asks here, so they cannot disagree. Each used to work the bill-to out for itself, three
 * different ways, and all of them printed the customer as the consignee.
 *
 * <p>The bill-to is always the customer: that is who the tax is charged to, and under section
 * 10(1)(b) of the IGST Act it is the bill-to party's state, not the delivery address, that decides
 * the place of supply when goods are delivered to a third party. The ship-to is the customer too
 * unless the order names a different consignee.
 */
public final class SalesParties {

    private SalesParties() {
    }

    public static DocumentParty billTo(SalesOrder so) {
        Contact customer = so.getCustomer();
        if (customer == null) {
            return new DocumentParty(null, text(so.getBillToAddress()), null, null);
        }
        String address = hasText(so.getBillToAddress())
                ? so.getBillToAddress().trim()
                : format(billingAddressOf(customer));
        return new DocumentParty(customer.getCompanyName(), address, text(customer.getGstNumber()), stateCodeOf(customer));
    }

    public static DocumentParty shipTo(SalesOrder so) {
        DocumentParty billTo = billTo(so);

        boolean otherParty = hasText(so.getShipToName())
                && (billTo.getName() == null || !so.getShipToName().trim().equalsIgnoreCase(billTo.getName().trim()));
        boolean otherAddress = hasText(so.getDeliveryAddress());

        String name = otherParty ? so.getShipToName().trim() : billTo.getName();
        String address = otherAddress ? so.getDeliveryAddress().trim() : billTo.getAddress();

        // A consignee who is somebody else never inherits the customer's GSTIN.
        String gstin = hasText(so.getShipToGstin()) ? so.getShipToGstin().trim()
                : otherParty ? null : billTo.getGstin();

        // The state is only known when it was given, when the consignee's own GSTIN gives it, or when
        // the goods go to the billing address. A typed delivery address says nothing reliable.
        String stateCode;
        if (hasText(so.getShipToStateCode())) {
            stateCode = so.getShipToStateCode().trim();
        } else if (hasText(so.getShipToGstin())) {
            stateCode = GstResolver.parseStateCodeFromGstin(so.getShipToGstin().trim());
        } else if (!otherParty && (!otherAddress || address.equalsIgnoreCase(billTo.getAddress()))) {
            stateCode = billTo.getStateCode();
        } else {
            stateCode = null;
        }
        return new DocumentParty(name, address, gstin, stateCode);
    }

    /** The parties as issued. An invoice raised before it kept its own copy reads from its order. */
    public static DocumentParty billTo(TaxInvoice invoice) {
        if (hasText(invoice.getBillToName())) {
            return new DocumentParty(invoice.getBillToName(), invoice.getBillToAddress(),
                    invoice.getBillToGstin(), invoice.getBillToStateCode());
        }
        return invoice.getSalesOrder() != null ? billTo(invoice.getSalesOrder())
                : new DocumentParty(null, null, null, null);
    }

    public static DocumentParty shipTo(TaxInvoice invoice) {
        if (hasText(invoice.getShipToName())) {
            return new DocumentParty(invoice.getShipToName(), invoice.getShipToAddress(),
                    invoice.getShipToGstin(), invoice.getShipToStateCode());
        }
        return invoice.getSalesOrder() != null ? shipTo(invoice.getSalesOrder())
                : new DocumentParty(null, null, null, null);
    }

    /** Copies both parties onto the invoice, so that later edits to the order or the customer cannot reach it. */
    public static void freezeOnto(TaxInvoice invoice, SalesOrder so) {
        DocumentParty billTo = billTo(so);
        DocumentParty shipTo = shipTo(so);
        invoice.setBillToName(billTo.getName());
        invoice.setBillToAddress(billTo.getAddress());
        invoice.setBillToGstin(billTo.getGstin());
        invoice.setBillToStateCode(billTo.getStateCode());
        invoice.setShipToName(shipTo.getName());
        invoice.setShipToAddress(shipTo.getAddress());
        invoice.setShipToGstin(shipTo.getGstin());
        invoice.setShipToStateCode(shipTo.getStateCode());
    }

    /**
     * The address a customer is billed at: a billing address before any other kind, and the default
     * one before the rest. A customer with only a shipping address on file is still billed there
     * rather than nowhere.
     */
    static ContactAddress billingAddressOf(Contact customer) {
        List<ContactAddress> all = customer.getAddresses() != null ? customer.getAddresses() : List.of();
        Predicate<ContactAddress> billing = a -> a.getAddressType() == null
                || a.getAddressType() == AddressType.BILLING || a.getAddressType() == AddressType.BOTH;

        return first(all, billing.and(ContactAddress::isDefault))
                .or(() -> first(all, billing))
                .or(() -> first(all, ContactAddress::isDefault))
                .or(() -> all.stream().findFirst())
                .orElse(null);
    }

    static String format(ContactAddress a) {
        if (a == null) return null;
        String line = Stream.of(a.getStreet1(), a.getStreet2(), a.getCity(), a.getState())
                .filter(SalesParties::hasText)
                .map(String::trim)
                .collect(Collectors.joining(", "));
        if (hasText(a.getPinCode())) {
            line += (line.isEmpty() ? "" : " - ") + a.getPinCode().trim();
        }
        return line.isEmpty() ? null : line;
    }

    private static String stateCodeOf(Contact c) {
        return GstResolver.stateCodeOf(c.getStateCode(), c.getGstNumber());
    }

    private static Optional<ContactAddress> first(List<ContactAddress> all, Predicate<ContactAddress> test) {
        return all.stream().filter(test).findFirst();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String text(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
