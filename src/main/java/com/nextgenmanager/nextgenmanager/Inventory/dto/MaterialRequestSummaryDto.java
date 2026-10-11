package com.nextgenmanager.nextgenmanager.Inventory.dto;

import java.util.Date;

/**
 * The counts above the material request register. Sales order reservations are counted apart:
 * they are not the stores' to approve, so they never inflate what is waiting.
 */
public record MaterialRequestSummaryDto(
        long waiting,
        long partlyIssued,
        long approvedLast7Days,
        long rejectedLast7Days,
        long salesOrderReservations,
        // When the oldest request still waiting was raised; null when nothing is waiting.
        Date oldestWaiting
) {}
