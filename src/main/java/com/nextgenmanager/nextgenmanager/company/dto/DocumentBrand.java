package com.nextgenmanager.nextgenmanager.company.dto;

import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.model.DocumentBranding;
import lombok.Getter;

import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a print template needs to head a document, exposed to every template as {@code brand}.
 *
 * <p>The sizes are worked out here, in millimetres, and never left to the image: an uploaded image
 * is scaled to fit inside a fixed box, so the height a header takes is known before the page is laid
 * out. That is what lets the fixed-height documents (invoice, proforma, order acknowledgement) keep
 * their page count — they ask {@link #getBandHeightMm()} and give up item rows to pay for it.
 */
@Getter
public class DocumentBrand {

    /** Logo beside the company text on customer-facing documents. */
    public static final double LOGO_BOX_WIDTH_MM = 45;
    public static final double LOGO_BOX_HEIGHT_MM = 18;

    /** Logo beside the company name on shop-floor documents. */
    public static final double SMALL_LOGO_BOX_WIDTH_MM = 24;
    public static final double SMALL_LOGO_BOX_HEIGHT_MM = 8;

    /**
     * Letterhead band. 174mm is the narrowest content width any customer-facing template has, so a
     * letterhead of ordinary proportions runs edge to edge. It may be up to 40mm tall, which is the
     * deepest that still leaves every document on the sheets it needs with a text header; the
     * documents make room for it by giving up item rows and blank filler rows.
     */
    public static final double BAND_WIDTH_MM = 174;
    public static final double BAND_MAX_HEIGHT_MM = 40;

    /** The blank band left at the top of pre-printed paper. */
    public static final double PREPRINTED_BAND_HEIGHT_MM = 30;

    private final BrandingMode mode;
    private final String companyName;
    private final String statutoryLine;

    private final String logoUri;
    private final String logoStyle;
    private final String logoCellStyle;
    private final String smallLogoStyle;

    private final String bannerUri;
    private final String bannerStyle;
    private final String bandStyle;
    private final double bandHeightMm;

    private DocumentBrand(BrandingMode mode, String companyName, String statutoryLine,
                          String logoUri, double[] logoMm, double[] smallLogoMm,
                          String bannerUri, double[] bannerMm) {
        this.mode = mode;
        this.companyName = companyName;
        this.statutoryLine = statutoryLine;
        this.logoUri = logoUri;
        this.logoStyle = logoUri != null ? sizeStyle(logoMm) : null;
        // The cell that holds the logo is exactly as wide as the logo plus a gutter, so the company
        // text beside it gets all the remaining width.
        this.logoCellStyle = logoUri != null
                ? "width:" + mm(logoMm[0] + 5) + ";padding:0;border:none;vertical-align:middle;text-align:left;"
                : null;
        this.smallLogoStyle = logoUri != null ? "vertical-align:middle;" + sizeStyle(smallLogoMm) : null;

        if (mode == BrandingMode.LETTERHEAD) {
            this.bannerUri = bannerUri;
            this.bannerStyle = "display:block;margin:0 auto;" + sizeStyle(bannerMm);
            this.bandHeightMm = bannerMm[1];
        } else {
            this.bannerUri = null;
            this.bannerStyle = null;
            this.bandHeightMm = mode == BrandingMode.PREPRINTED ? PREPRINTED_BAND_HEIGHT_MM : 0;
        }
        this.bandStyle = "height:" + mm(bandHeightMm) + ";text-align:center;font-size:0;line-height:0;overflow:hidden;";
    }

    /**
     * @param branding     the stored settings, or null when none have been saved
     * @param company      the company row, or null when it has not been filled in
     * @param modeOverride a mode to render instead of the saved one (the settings preview), or null
     */
    public static DocumentBrand of(DocumentBranding branding, CompanyDetails company, BrandingMode modeOverride) {
        String companyName = company != null && company.getCompanyName() != null ? company.getCompanyName() : "";
        String statutoryLine = company == null ? null : blankToNull(Stream.of(
                        labelled("GSTIN", company.getGstNumber()),
                        labelled("PAN", company.getPanNumber()),
                        labelled("CIN", company.getCinNumber()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("   |   ")));

        boolean hasLogo = branding != null && branding.getLogoImage() != null;
        boolean hasLetterhead = branding != null && branding.getLetterheadImage() != null;

        BrandingMode mode = modeOverride != null ? modeOverride
                : branding != null && branding.getMode() != null ? branding.getMode() : BrandingMode.NONE;
        // A mode whose image is missing prints as plain text rather than as a hole in the page.
        if (mode == BrandingMode.LOGO && !hasLogo) mode = BrandingMode.NONE;
        if (mode == BrandingMode.LETTERHEAD && !hasLetterhead) mode = BrandingMode.NONE;

        String logoUri = null;
        double[] logoMm = null;
        double[] smallLogoMm = null;
        if (hasLogo && mode != BrandingMode.NONE) {
            logoUri = dataUri(branding.getLogoContentType(), branding.getLogoImage());
            logoMm = fit(branding.getLogoWidthPx(), branding.getLogoHeightPx(), LOGO_BOX_WIDTH_MM, LOGO_BOX_HEIGHT_MM);
            smallLogoMm = fit(branding.getLogoWidthPx(), branding.getLogoHeightPx(),
                    SMALL_LOGO_BOX_WIDTH_MM, SMALL_LOGO_BOX_HEIGHT_MM);
        }

        String bannerUri = null;
        double[] bannerMm = null;
        if (mode == BrandingMode.LETTERHEAD) {
            bannerUri = dataUri(branding.getLetterheadContentType(), branding.getLetterheadImage());
            bannerMm = fit(branding.getLetterheadWidthPx(), branding.getLetterheadHeightPx(),
                    BAND_WIDTH_MM, BAND_MAX_HEIGHT_MM);
        }

        return new DocumentBrand(mode, companyName, statutoryLine, logoUri, logoMm, smallLogoMm, bannerUri, bannerMm);
    }

    /** The letterhead image or the blank band stands in for the template's own text header. */
    public boolean isReplacesHeader() {
        return mode == BrandingMode.LETTERHEAD || mode == BrandingMode.PREPRINTED;
    }

    public boolean isBanner() {
        return mode == BrandingMode.LETTERHEAD;
    }

    /** The logo sits beside the company text on customer-facing documents. */
    public boolean isLogo() {
        return mode == BrandingMode.LOGO;
    }

    /** Shop-floor documents carry the logo whenever one has been uploaded and branding is on. */
    public boolean isSmallLogo() {
        return logoUri != null;
    }

    public boolean isCompanyNamed() {
        return !companyName.isBlank();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Largest size, in mm, at which a widthPx × heightPx image fits inside the box without distortion. */
    static double[] fit(Integer widthPx, Integer heightPx, double boxWidthMm, double boxHeightMm) {
        if (widthPx == null || heightPx == null || widthPx <= 0 || heightPx <= 0) {
            return new double[]{boxWidthMm, boxHeightMm};
        }
        double mmPerPx = Math.min(boxWidthMm / widthPx, boxHeightMm / heightPx);
        return new double[]{widthPx * mmPerPx, heightPx * mmPerPx};
    }

    private static String sizeStyle(double[] mm) {
        return "width:" + mm(mm[0]) + ";height:" + mm(mm[1]) + ";";
    }

    private static String mm(double value) {
        return String.format(Locale.ROOT, "%.1fmm", value);
    }

    private static String dataUri(String contentType, byte[] data) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(data);
    }

    private static String labelled(String label, String value) {
        return value == null || value.isBlank() ? null : label + ": " + value.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
