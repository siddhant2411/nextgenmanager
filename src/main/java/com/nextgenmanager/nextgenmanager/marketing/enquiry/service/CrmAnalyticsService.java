package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmAnalyticsDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquirySummaryDTO;

/**
 * The CRM dashboard's two reads.
 *
 * <p>Split out of {@code EnquiryService} rather than added to it: that interface is about the
 * lifecycle of an enquiry — create, update, close, convert — and reporting has different
 * transactional characteristics, a different cache story and a different audience. Keeping the
 * summary there is what let it quietly stay unbounded for so long.
 */
public interface CrmAnalyticsService {

    /**
     * Headline figures for a window, plus the same figures for the preceding window and the
     * as-of-today stock block.
     */
    EnquirySummaryDTO getSummary(CrmPeriod period);

    /** Funnel, trend and every grouped breakdown, for the same window. */
    CrmAnalyticsDTO getAnalytics(CrmPeriod period);
}
