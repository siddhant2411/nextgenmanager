package com.nextgenmanager.nextgenmanager.sales.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published when a Sales Order is approved (and its inventory needs have been raised).
 * The procurement module listens and routes any order-linked (MAKE_TO_ORDER) shortfall
 * to a Work Order or Purchase Requisition. Sales has zero knowledge of procurement.
 */
@Getter
@AllArgsConstructor
public class SalesOrderApprovedEvent {
    private final Long salesOrderId;
    private final Instant occurredAt = Instant.now();
}
