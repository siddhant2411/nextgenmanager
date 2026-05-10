package com.nextgenmanager.nextgenmanager.purchase.service;

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
        if (StringUtils.hasText(vendor.getStateCode())) {
            return vendor.getStateCode();
        }
        return parseStateCodeFromGstin(vendor.getGstNumber());
    }

    /**
     * Returns the 2-digit state code for the buying company.
     * Priority: stored stateCode → parsed from GSTIN → null.
     */
    public String resolveCompanyStateCode() {
        return companyDetailsRepository.findAll().stream().findFirst()
                .map(c -> StringUtils.hasText(c.getStateCode())
                        ? c.getStateCode()
                        : parseStateCodeFromGstin(c.getGstNumber()))
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

    /** Extracts first 2 chars of a valid 15-char GSTIN as the state code. */
    public static String parseStateCodeFromGstin(String gstin) {
        if (StringUtils.hasText(gstin) && gstin.length() >= 2) {
            return gstin.substring(0, 2);
        }
        return null;
    }
}
