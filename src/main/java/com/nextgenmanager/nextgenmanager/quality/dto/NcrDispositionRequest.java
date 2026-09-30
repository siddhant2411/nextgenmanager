package com.nextgenmanager.nextgenmanager.quality.dto;

import com.nextgenmanager.nextgenmanager.quality.model.NcrDisposition;

/**
 * What is to be done about the goods. USE_AS_IS needs an approver: it is the disposition that
 * overrides an inspection rather than acting on it.
 */
public record NcrDispositionRequest(
        NcrDisposition disposition,
        String dispositionedBy,
        String approvedBy,
        String notes
) {}
