package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * One product's contribution to order intake in the window.
 *
 * <p>{@link #getCustomerCount()} is the column that turns a ranking into a decision. A product
 * that sold heavily to one customer and a product that sold the same value across eleven are the
 * same row on a revenue chart and completely different businesses: the first is a concentration
 * risk wearing a top-seller badge.
 */
public interface TopProductRow {

    Integer getItemId();

    String getItemCode();

    String getItemName();

    /** {@code itemGroupCode}, or "Ungrouped" — never a null that silently drops the row. */
    String getItemGroup();

    /** Summed in the item's own UOM. Only comparable within a row, never across rows. */
    BigDecimal getQty();

    /** Line value after the order's header discount has been allocated to it. */
    BigDecimal getRevenue();

    Long getOrderCount();

    Long getCustomerCount();
}
