package com.nextgenmanager.nextgenmanager.accounting.tds.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a TDS challan is recorded. The posting listener clears TDS Payable against
 * the bank (Dr 9015 / Cr Bank).
 */
@Getter
@AllArgsConstructor
public class TdsChallanDepositedEvent {
    private final Long challanId;
    private final Instant occurredAt = Instant.now();
}
