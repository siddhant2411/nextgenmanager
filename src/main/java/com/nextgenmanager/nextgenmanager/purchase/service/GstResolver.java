package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.common.gst.GstState;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.GstType;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GstResolver {

    private final CompanyDetailsRepository companyDetailsRepository;

    public GstResolver(CompanyDetailsRepository companyDetailsRepository) {
        this.companyDetailsRepository = companyDetailsRepository;
    }

    /**
     * Returns the 2-digit GST state code for a contact.
     * Priority: stored stateCode → parsed from GSTIN → null.
     */
    public String resolveVendorStateCode(Contact vendor) {
        return stateCodeOf(vendor.getStateCode(), vendor.getGstNumber());
    }

    /**
     * Returns the 2-digit state code for the buying company.
     * Priority: stored stateCode → parsed from GSTIN → null.
     */
    public String resolveCompanyStateCode() {
        return companyDetailsRepository.findAll().stream().findFirst()
                .map(c -> stateCodeOf(c.getStateCode(), c.getGstNumber()))
                .orElse(null);
    }

    /**
     * Derives the GST treatment by comparing vendor's state with company's state.
     * Falls back to UNREGISTERED when vendor has no GSTIN.
     */
    public GstTreatment deriveGstTreatment(Contact vendor) {
        if (vendor.getGstType() == GstType.COMPOSITION) {
            return GstTreatment.COMPOSITION;
        }
        if (!StringUtils.hasText(vendor.getGstNumber())) {
            return GstTreatment.UNREGISTERED;
        }
        String vendorState  = resolveVendorStateCode(vendor);
        String companyState = resolveCompanyStateCode();
        if (vendorState == null || companyState == null) {
            return GstTreatment.INTER_STATE;
        }
        return vendorState.equals(companyState)
                ? GstTreatment.INTRA_STATE
                : GstTreatment.INTER_STATE;
    }

    /** A party's state: the code stored on it, else the one its GSTIN was registered in, else null. */
    public static String stateCodeOf(String storedStateCode, String gstin) {
        GstState stored = GstState.fromCode(storedStateCode);
        return stored != null ? stored.getCode() : parseStateCodeFromGstin(gstin);
    }

    /** The first 2 chars of a GSTIN when they are a GST state code; null otherwise. */
    public static String parseStateCodeFromGstin(String gstin) {
        return GstState.codeFromGstin(gstin);
    }

    /**
     * The state code to store on a party when it is saved. A GSTIN settles it, since the first two
     * digits are the state of registration; an unregistered party has what was chosen for it, and
     * failing that the state named on its address.
     */
    public static String stateCodeToStore(String gstin, String chosenStateCode, String addressStateName) {
        String fromGstin = parseStateCodeFromGstin(gstin);
        if (fromGstin != null) return fromGstin;
        String chosen = GstState.requireCode(chosenStateCode, "State code");
        if (chosen != null) return chosen;
        GstState named = GstState.fromName(addressStateName);
        return named != null ? named.getCode() : null;
    }
}
