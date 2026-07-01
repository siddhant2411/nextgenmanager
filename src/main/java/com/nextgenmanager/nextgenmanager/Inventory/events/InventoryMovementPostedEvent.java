package com.nextgenmanager.nextgenmanager.Inventory.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Published after a valued {@code InventoryLedger} row is persisted by the inventory funnel
 * ({@code InventoryTransactionServiceImpl}). Accounting listens and posts the perpetual-inventory
 * voucher for value-changing movements (GRN, WO consume/produce, sales dispatch, adjustment,
 * WO return); availability/location-only moves (reserve, issue-to-floor) are ignored downstream.
 */
@Getter
@AllArgsConstructor
public class InventoryMovementPostedEvent {
    private final Long inventoryLedgerId;
    private final Instant occurredAt = Instant.now();
}
