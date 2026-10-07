package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.common.gst.GstState;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One party block on a sales document — the bill-to or the ship-to — exactly as it is printed.
 * Every field may be null; a template prints the lines that are there.
 */
@Getter
@AllArgsConstructor
public class DocumentParty {

    private final String name;
    private final String address;
    private final String gstin;
    /** 2-digit GST state code. */
    private final String stateCode;

    /** "Gujarat (24)", or null when the state is not known. */
    public String getStateLabel() {
        return stateCode == null || stateCode.isBlank() ? null : GstState.labelFor(stateCode);
    }
}
