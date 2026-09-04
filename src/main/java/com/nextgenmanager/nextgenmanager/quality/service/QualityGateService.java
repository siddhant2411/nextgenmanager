package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;

import java.util.List;

/**
 * Whether quality lets a movement happen.
 *
 * <p>Separate from the recording of inspections on purpose: measurement was never the gap. This is
 * the half that says no.
 */
public interface QualityGateService {

    /**
     * Why this work order may not produce finished goods, in words a shop-floor supervisor can
     * act on. Empty means nothing is in the way.
     */
    List<String> reasonsProductionIsBlocked(WorkOrder workOrder);

    /**
     * The same check, as a guard. Throws {@link IllegalStateException} listing every reason rather
     * than the first one found — a supervisor who fixes one problem and is then told about the
     * next is being sent round the loop twice.
     */
    void assertProductionAllowed(WorkOrder workOrder);
}
