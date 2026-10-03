package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;

import java.util.List;

public interface PackagingGateService {

    /** Every reason this slip cannot close yet. Empty means it can. */
    List<String> reasonsClosingIsBlocked(PackingSlip slip);

    /** Throws with all reasons joined, or returns silently. */
    void assertClosingAllowed(PackingSlip slip);
}
