package com.nextgenmanager.nextgenmanager.common.gst;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indian GST state / UT codes as defined in CGST Act Schedule.
 *
 * <p>Fixed by law, so they are written out here rather than maintained by users. The same rows are
 * seeded into the {@code gstState} table (V180), which every stored state code is a foreign key to;
 * {@code GstStateTest} fails if the two lists drift apart.
 */
public enum GstState {

    // @formatter:off
    JAMMU_KASHMIR               ("01", "Jammu & Kashmir"),
    HIMACHAL_PRADESH            ("02", "Himachal Pradesh"),
    PUNJAB                      ("03", "Punjab"),
    CHANDIGARH                  ("04", "Chandigarh"),
    UTTARAKHAND                 ("05", "Uttarakhand"),
    HARYANA                     ("06", "Haryana"),
    DELHI                       ("07", "Delhi"),
    RAJASTHAN                   ("08", "Rajasthan"),
    UTTAR_PRADESH               ("09", "Uttar Pradesh"),
    BIHAR                       ("10", "Bihar"),
    SIKKIM                      ("11", "Sikkim"),
    ARUNACHAL_PRADESH           ("12", "Arunachal Pradesh"),
    NAGALAND                    ("13", "Nagaland"),
    MANIPUR                     ("14", "Manipur"),
    MIZORAM                     ("15", "Mizoram"),
    TRIPURA                     ("16", "Tripura"),
    MEGHALAYA                   ("17", "Meghalaya"),
    ASSAM                       ("18", "Assam"),
    WEST_BENGAL                 ("19", "West Bengal"),
    JHARKHAND                   ("20", "Jharkhand"),
    ODISHA                      ("21", "Odisha"),
    CHHATTISGARH                ("22", "Chhattisgarh"),
    MADHYA_PRADESH              ("23", "Madhya Pradesh"),
    GUJARAT                     ("24", "Gujarat"),
    DAMAN_DIU_OLD               ("25", "Daman & Diu (pre-2020)"),
    DADRA_NAGAR_HAVELI          ("26", "Dadra & Nagar Haveli and Daman & Diu"),
    MAHARASHTRA                 ("27", "Maharashtra"),
    ANDHRA_PRADESH_OLD          ("28", "Andhra Pradesh (pre-2014)"),
    KARNATAKA                   ("29", "Karnataka"),
    GOA                         ("30", "Goa"),
    LAKSHADWEEP                 ("31", "Lakshadweep"),
    KERALA                      ("32", "Kerala"),
    TAMIL_NADU                  ("33", "Tamil Nadu"),
    PUDUCHERRY                  ("34", "Puducherry"),
    ANDAMAN_NICOBAR             ("35", "Andaman & Nicobar Islands"),
    TELANGANA                   ("36", "Telangana"),
    ANDHRA_PRADESH              ("37", "Andhra Pradesh"),
    LADAKH                      ("38", "Ladakh"),
    OTHER_TERRITORY             ("97", "Other Territory"),
    CENTRE_JURISDICTION         ("99", "Centre Jurisdiction");
    // @formatter:on

    private final String code;
    private final String displayName;

    GstState(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode()        { return code; }
    public String getDisplayName() { return displayName; }

    /** The state with this 2-digit code, or null. */
    public static GstState fromCode(String code) {
        if (code == null) return null;
        String c = code.trim();
        for (GstState s : values()) {
            if (s.code.equals(c)) return s;
        }
        return null;
    }

    /** The state with this name as typed on an address ("gujarat", "Jammu and Kashmir"), or null. */
    public static GstState fromName(String name) {
        if (name == null || name.isBlank()) return null;
        String n = normalise(name);
        for (GstState s : values()) {
            if (normalise(s.displayName).equals(n)) return s;
        }
        return null;
    }

    /** The state a GSTIN is registered in — its first two characters — or null if they are not a state code. */
    public static String codeFromGstin(String gstin) {
        if (gstin == null || gstin.trim().length() < 2) return null;
        GstState s = fromCode(gstin.trim().substring(0, 2));
        return s != null ? s.code : null;
    }

    /**
     * A state code as it may be stored: null for blank, the code itself when it is one of ours.
     * Anything else is refused here, by name, rather than by the foreign key underneath.
     */
    public static String requireCode(String code, String field) {
        if (code == null || code.isBlank()) return null;
        GstState s = fromCode(code);
        if (s == null) {
            throw new IllegalArgumentException(field + " \"" + code.trim()
                    + "\" is not a GST state code. Use a 2-digit code such as 24 (Gujarat) or 27 (Maharashtra).");
        }
        return s.code;
    }

    /** Returns "Gujarat (24)" style label, or the raw code if unrecognised. */
    public static String labelFor(String code) {
        if (code == null) return null;
        GstState s = fromCode(code);
        return s != null ? s.displayName + " (" + s.code + ")" : code;
    }

    /** Returns just the display name, or the raw code if unrecognised. */
    public static String nameFor(String code) {
        if (code == null) return null;
        GstState s = fromCode(code);
        return s != null ? s.displayName : code;
    }

    private static String normalise(String name) {
        return name.toLowerCase().replace("&", "and").replaceAll("[^a-z]", "");
    }

    /** Returns all states as [{code, name}] ordered by code — for dropdown APIs. */
    public static List<Map<String, String>> toList() {
        return Arrays.stream(values())
                .map(s -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("code", s.code);
                    m.put("name", s.displayName);
                    return m;
                })
                .toList();
    }
}
