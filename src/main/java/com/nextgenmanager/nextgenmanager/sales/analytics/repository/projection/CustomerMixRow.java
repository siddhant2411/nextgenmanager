package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * The new-versus-repeat split for a window.
 *
 * <h3>What "repeat" means here</h3>
 * A customer is <em>repeat</em> if they placed at least one live order at any time <strong>before
 * the window opens</strong> — not if they ordered twice inside it. The cohort question a
 * manufacturer actually asks is "how much of this quarter came from people who already knew us?",
 * and that is a question about history, not about within-window frequency.
 *
 * <p>The consequence worth knowing: on an ALL_TIME window there is no "before", so every customer
 * classifies as new and repeat revenue reads zero. That is arithmetically correct and completely
 * useless, so the UI says so rather than drawing a chart implying the business has no loyal
 * customers.
 */
public interface CustomerMixRow {

    Long getNewCustomers();

    Long getRepeatCustomers();

    BigDecimal getNewRevenue();

    BigDecimal getRepeatRevenue();

    Long getNewOrders();

    Long getRepeatOrders();
}
