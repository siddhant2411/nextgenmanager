package com.nextgenmanager.nextgenmanager.common.gst;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indian GST state / UT codes as defined in CGST Act Schedule.
 * Immutable by law — no DB table needed.
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

    /** Returns "Gujarat (24)" style label, or the raw code if unrecognised. */
    public static String labelFor(String code) {
        if (code == null) return null;
        for (GstState s : values()) {
            if (s.code.equals(code)) return s.displayName + " (" + code + ")";
        }
        return code;
    }

    /** Returns just the display name, or the raw code if unrecognised. */
    public static String nameFor(String code) {
        if (code == null) return null;
        for (GstState s : values()) {
            if (s.code.equals(code)) return s.displayName;
        }
        return code;
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
