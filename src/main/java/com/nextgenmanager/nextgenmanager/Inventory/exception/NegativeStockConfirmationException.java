package com.nextgenmanager.nextgenmanager.Inventory.exception;

import java.math.BigDecimal;

/**
 * Raised when approving a material request would drive a (non batch/serial-tracked) item's available
 * stock below zero. It is not an error — it signals that the approver must explicitly confirm the
 * shortfall. The API surfaces it as HTTP 409 with {@code requiresConfirmation=true}; the client
 * re-submits the same approval with {@code force=true}.
 *
 * <p>Batch/serial-tracked items never reach this exception — negative stock is impossible for them,
 * so their approvals are hard-blocked instead.
 */
public class NegativeStockConfirmationException extends RuntimeException {

    private final String itemCode;
    private final double available;
    private final double requested;
    private final double resultingBalance;

    public NegativeStockConfirmationException(String itemCode, double available, double requested) {
        super(String.format(
                "Approving %s of %s will take available stock below zero (%s → %s). Confirm to proceed.",
                fmt(requested), itemCode, fmt(available), fmt(available - requested)));
        this.itemCode = itemCode;
        this.available = available;
        this.requested = requested;
        this.resultingBalance = available - requested;
    }

    public String getItemCode()        { return itemCode; }
    public double getAvailable()        { return available; }
    public double getRequested()        { return requested; }
    public double getResultingBalance() { return resultingBalance; }

    private static String fmt(double d) {
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }
}
