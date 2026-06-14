package com.nextgenmanager.nextgenmanager.common.events;

/**
 * Canonical {@code sourceDocType} strings stored on auto-posted vouchers. Shared by the
 * posting listeners (which set them) and the cancel hooks (which void by them) so the two
 * sides cannot drift.
 */
public final class SourceDocTypes {

    private SourceDocTypes() {}

    public static final String TAX_INVOICE = "TAX_INVOICE";
    public static final String SALES_PAYMENT = "SALES_PAYMENT";
    public static final String SALES_CREDIT_NOTE = "SALES_CREDIT_NOTE";
    public static final String VENDOR_INVOICE = "VENDOR_INVOICE";
    public static final String VENDOR_PAYMENT = "VENDOR_PAYMENT";
    public static final String DEBIT_NOTE = "DEBIT_NOTE";
}
