package com.nextgenmanager.nextgenmanager.accounting.voucher.model;

public enum VoucherType {
    JOURNAL, SALES, PURCHASE, RECEIPT, PAYMENT, CONTRA,
    CREDIT_NOTE, DEBIT_NOTE, OPENING, CLOSING, DEPRECIATION, PAYROLL;

    public String prefix() {
        return switch (this) {
            case JOURNAL     -> "JNL";
            case SALES       -> "SAL";
            case PURCHASE    -> "PUR";
            case RECEIPT     -> "REC";
            case PAYMENT     -> "PAY";
            case CONTRA      -> "CON";
            case CREDIT_NOTE -> "CRN";
            case DEBIT_NOTE  -> "DBN";
            case OPENING     -> "OPN";
            case CLOSING     -> "CLO";
            case DEPRECIATION-> "DEP";
            case PAYROLL     -> "PAY";
        };
    }
}
