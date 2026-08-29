package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * Revenue rolled up to a product family ({@code itemGroupCode}).
 *
 * <p>Separate from {@link TopProductRow} rather than derived from it in Java: a top-20 product list
 * summed by group is not the same as the group totals, because the tail that falls outside the top
 * 20 belongs to some group too. Rolling up a truncated list is one of the easier ways to publish a
 * chart whose bars do not add up to the headline above them.
 */
public interface GroupRevenueRow {

    String getGroupCode();

    BigDecimal getRevenue();

    Long getLineCount();

    Long getItemCount();
}
