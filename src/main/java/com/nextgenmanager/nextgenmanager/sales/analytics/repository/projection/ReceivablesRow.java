package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * What is invoiced but not yet in the bank, as of today.
 *
 * <p><strong>Stock, not flow.</strong> {@code paidAmount} on an invoice is a running total with no
 * date attached — the invoice knows how much has been collected, not when. Bounding outstanding to
 * a window would therefore compare a period-filtered invoice set against an all-time payment
 * figure and report negative debt in a good quarter. Accounting's payment ledger is where a true
 * period-bounded collections figure lives; this row is deliberately the simpler, honest one.
 */
public interface ReceivablesRow {

    Long getOpenInvoiceCount();

    /** Invoiced gross, including GST — this is a cash figure, not a revenue figure. */
    BigDecimal getInvoicedTotal();

    BigDecimal getCollected();

    BigDecimal getOutstanding();

    /** Outstanding on invoices whose due date has passed. */
    BigDecimal getOverdue();

    Long getOverdueInvoiceCount();
}
