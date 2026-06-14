package com.nextgenmanager.nextgenmanager.items.model;

/**
 * How demand for an item is fulfilled when stock is short.
 *
 * <p>MAKE_TO_STOCK — item is kept on hand at a minimum level. A sales order reserves
 * available stock; replenishment is driven independently by reorder rules
 * (reorderLevel / minStock / maxStock), not by the order itself.
 *
 * <p>MAKE_TO_ORDER — item is procured/manufactured specifically for the order. A sales
 * order with a shortfall raises a procurement need chained to that order, which is then
 * routed to a Work Order (manufacture) or Purchase Requisition (buy).
 */
public enum ReplenishmentStrategy {
    MAKE_TO_STOCK,
    MAKE_TO_ORDER
}
