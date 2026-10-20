package com.nextgenmanager.nextgenmanager.common.audit;

/** The verbs and nouns of the audit trail, shared by every module that writes to it. */
public final class AuditActions {
    private AuditActions() {}

    // ─ Actions ─
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    /** A voucher entered the books. */
    public static final String POST = "POST";
    /** A manual voucher was saved and is waiting for approval. */
    public static final String SUBMIT = "SUBMIT";
    public static final String APPROVE = "APPROVE";
    public static final String REVERSE = "REVERSE";
    /** A source document was cancelled or deleted. */
    public static final String VOID = "VOID";
    /** A voided document's voucher could not be reversed automatically — the books still carry it. */
    public static final String AUTO_REVERSAL_FAILED = "AUTO_REVERSAL_FAILED";

    // ─ Entity types (source documents use their SourceDocTypes name) ─
    public static final String LEDGER = "LEDGER";
    public static final String ACCOUNT_GROUP = "ACCOUNT_GROUP";
    public static final String VOUCHER = "VOUCHER";
}
