package com.nextgenmanager.nextgenmanager.accounting.voucher.exception;

public class UnbalancedVoucherException extends RuntimeException {
    public UnbalancedVoucherException(String totalDr, String totalCr) {
        super("Voucher is unbalanced: total Dr=" + totalDr + " Cr=" + totalCr + ". They must be equal.");
    }
}
