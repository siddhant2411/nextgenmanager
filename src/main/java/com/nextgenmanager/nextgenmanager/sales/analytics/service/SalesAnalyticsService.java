package com.nextgenmanager.nextgenmanager.sales.analytics.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import com.nextgenmanager.nextgenmanager.sales.analytics.dto.SalesAnalyticsDTO;

/**
 * The Revenue Desk's single read.
 *
 * <p>Deliberately one method. The screen is a snapshot — headline, mix, trend, rankings and the
 * stock blocks all describing one moment — and splitting it would let a caller render half of a
 * period against half of another.
 *
 * <p>Kept apart from {@code SalesOrderService} for the same reason
 * {@code CrmAnalyticsService} was kept out of {@code EnquiryService}: that interface is about the
 * lifecycle of an order, and reporting has a different transactional profile, a different cache
 * story and a different audience.
 */
public interface SalesAnalyticsService {

    /**
     * Order intake, customer mix, product and customer rankings for the window, plus the as-of-today
     * stock blocks the window does not touch.
     *
     * @param period the window; {@link CrmPeriod#previous()} of it supplies every tile's delta
     */
    SalesAnalyticsDTO getAnalytics(CrmPeriod period);
}
