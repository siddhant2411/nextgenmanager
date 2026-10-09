package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.common.gst.GstState;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.ShipToKind;
import com.nextgenmanager.nextgenmanager.sales.dto.DocumentParty;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who a purchase order is billed to and where its goods are to be delivered — the purchase-side
 * twin of {@code SalesParties}.
 *
 * <p>The bill-to is always the company: it is the buyer, the vendor's invoice is made out to it and
 * the tax follows it, wherever the goods are sent. The ship-to is the company's registered address
 * unless the order names one of our plants or another party's address.
 */
public final class PurchaseParties {

    private PurchaseParties() {
    }

    public static ShipToKind kindOf(PurchaseOrder po) {
        if (po.getShipToWarehouse() != null) return ShipToKind.PLANT;
        if (po.getShipToAddress() != null) return ShipToKind.PARTY;
        return ShipToKind.COMPANY;
    }

    public static DocumentParty billTo(CompanyDetails company) {
        if (company == null) return new DocumentParty(null, null, null, null);
        return new DocumentParty(company.getCompanyName(),
                line(company.getStreet1(), company.getStreet2(), company.getCity(), company.getState(), company.getPinCode()),
                text(company.getGstNumber()),
                GstResolver.stateCodeOf(company.getStateCode(), company.getGstNumber()));
    }

    public static DocumentParty shipTo(PurchaseOrder po, CompanyDetails company) {
        DocumentParty billTo = billTo(company);

        Warehouse plant = po.getShipToWarehouse();
        if (plant != null) {
            // A plant is still the company. It carries its own GSTIN only where it is registered
            // separately; its state is taken from that, else from the state named on its address.
            String name = Stream.of(billTo.getName(), plant.getName())
                    .filter(PurchaseParties::hasText).collect(Collectors.joining(" - "));
            String address = line(plant.getAddressLine1(), plant.getAddressLine2(), plant.getCity(), plant.getState(), plant.getPincode());
            String gstin = hasText(plant.getGstin()) ? plant.getGstin().trim() : billTo.getGstin();
            String stateCode = hasText(plant.getGstin()) ? GstResolver.parseStateCodeFromGstin(plant.getGstin().trim())
                    : stateCodeForName(plant.getState());
            return new DocumentParty(name, address, gstin, stateCode);
        }

        ContactAddress address = po.getShipToAddress();
        if (address != null) {
            Contact party = address.getContact();
            String stateCode = party != null ? GstResolver.stateCodeOf(party.getStateCode(), party.getGstNumber()) : null;
            return new DocumentParty(
                    party != null ? party.getCompanyName() : null,
                    line(address.getStreet1(), address.getStreet2(), address.getCity(), address.getState(), address.getPinCode()),
                    party != null ? text(party.getGstNumber()) : null,
                    stateCode != null ? stateCode : stateCodeForName(address.getState()));
        }

        return billTo;
    }

    /** "street1, street2, city, state - pin", missing parts left out; the format sales documents print. */
    private static String line(String street1, String street2, String city, String state, String pin) {
        String joined = Stream.of(street1, street2, city, state)
                .filter(PurchaseParties::hasText)
                .map(String::trim)
                .collect(Collectors.joining(", "));
        if (hasText(pin)) {
            joined += (joined.isEmpty() ? "" : " - ") + pin.trim();
        }
        return joined.isEmpty() ? null : joined;
    }

    /** An address holds its state as a name; this finds the code for it, or null when it is not a state we know. */
    private static String stateCodeForName(String stateName) {
        if (!hasText(stateName)) return null;
        for (GstState s : GstState.values()) {
            if (s.getDisplayName().equalsIgnoreCase(stateName.trim()) || s.getCode().equals(stateName.trim())) {
                return s.getCode();
            }
        }
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String text(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
