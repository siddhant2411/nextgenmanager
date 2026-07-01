package com.nextgenmanager.nextgenmanager.accounting.gst;

import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared GST derivations for registers and returns — there is no place-of-supply or treatment
 * field on the source documents, so these are computed on the fly from the party master and
 * {@link CompanyDetails}. The company GSTIN is single (locked decision #4).
 *
 * <p>Reuses {@link GstResolver} for state-code resolution and intra/inter derivation.
 */
@Component
@RequiredArgsConstructor
public class GstSupport {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final GstResolver gstResolver;
    private final CompanyDetailsRepository companyRepo;

    // ── Company ──────────────────────────────────────────────────────────────

    public String companyGstin() {
        return companyRepo.findAll().stream().findFirst()
                .map(CompanyDetails::getGstNumber).orElse(null);
    }

    public String companyStateCode() {
        return gstResolver.resolveCompanyStateCode();
    }

    // ── Party ────────────────────────────────────────────────────────────────

    /** Trade name if present, else legal company name. */
    public String partyName(Contact c) {
        if (c == null) return null;
        return StringUtils.hasText(c.getTradeName()) ? c.getTradeName() : c.getCompanyName();
    }

    /** 2-digit place-of-supply state code: party stateCode, else parsed from GSTIN. */
    public String placeOfSupply(Contact c) {
        return c == null ? null : gstResolver.resolveVendorStateCode(c);
    }

    /** A registered party (has GSTIN) is B2B; otherwise B2C. */
    public boolean isB2B(Contact c) {
        return c != null && StringUtils.hasText(c.getGstNumber());
    }

    /** Inter-state when the party's state differs from the company's; unknown state defaults to inter-state. */
    public boolean isInterState(Contact c) {
        String company = companyStateCode();
        String party = placeOfSupply(c);
        if (!StringUtils.hasText(company) || !StringUtils.hasText(party)) return true;
        return !company.equals(party);
    }

    // ── Tax splitting ──────────────────────────────────────────────────────────

    /**
     * Splits a combined GST amount (credit/debit notes carry only a total) into
     * {@code [cgst, sgst, igst]} by treatment. Intra-state = 50/50 CGST+SGST, inter-state = IGST.
     */
    public BigDecimal[] splitGst(BigDecimal totalGst, boolean interState) {
        BigDecimal gst = totalGst != null ? totalGst : BigDecimal.ZERO;
        if (interState) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, gst};
        }
        BigDecimal cgst = gst.divide(TWO, 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{cgst, gst.subtract(cgst), BigDecimal.ZERO};
    }

    /** Effective tax rate % from taxable + total tax (rounded to whole number; 0 when taxable is 0). */
    public BigDecimal effectiveRate(BigDecimal taxable, BigDecimal totalTax) {
        if (taxable == null || taxable.signum() == 0) return BigDecimal.ZERO;
        return totalTax.multiply(BigDecimal.valueOf(100))
                .divide(taxable, 0, RoundingMode.HALF_UP);
    }
}
