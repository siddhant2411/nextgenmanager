package com.nextgenmanager.nextgenmanager.accounting.voucher.exception;

import java.time.LocalDate;

public class LockedPeriodException extends RuntimeException {
    public LockedPeriodException(LocalDate date, String periodStatus) {
        super("Cannot post to period containing " + date + ": period status is " + periodStatus);
    }
}
