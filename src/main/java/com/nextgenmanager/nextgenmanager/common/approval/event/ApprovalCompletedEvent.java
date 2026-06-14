package com.nextgenmanager.nextgenmanager.common.approval.event;

import lombok.Getter;

/** Published by ApprovalEngineImpl when the final approval step is approved. */
@Getter
public class ApprovalCompletedEvent {
    private final String documentType;
    private final Long documentId;
    private final String approvedBy;

    public ApprovalCompletedEvent(String documentType, Long documentId, String approvedBy) {
        this.documentType = documentType;
        this.documentId   = documentId;
        this.approvedBy   = approvedBy;
    }
}
