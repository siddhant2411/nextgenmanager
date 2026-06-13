package com.nextgenmanager.nextgenmanager.common.approval.service;

import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalRequestDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.RequiredStep;

import java.util.List;
import java.util.Map;

public interface ApprovalEngine {
    /**
     * Returns the ordered list of required approval steps for the given document type and context.
     * Returns empty list if auto-approve (no matching enabled rule).
     */
    List<RequiredStep> evaluate(String documentType, Map<String, Object> context);

    /** Creates an ApprovalRequest with steps for a document that needs approval. */
    ApprovalRequestDto submit(String documentType, Long documentId, Map<String, Object> context, String requestedBy);

    /** Approves or rejects one step. Publishes ApprovalCompletedEvent when the final step is approved. */
    void act(Long stepId, boolean approved, String comment, String actedBy);

    /** Returns the current approval request for a document, or null. */
    ApprovalRequestDto getRequest(String documentType, Long documentId);
}
