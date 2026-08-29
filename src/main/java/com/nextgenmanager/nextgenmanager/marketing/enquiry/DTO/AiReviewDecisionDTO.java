package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.Data;

/**
 * A salesperson's verdict on an enquiry the AI Lead Agent raised.
 *
 * Separate from the ordinary update path on purpose. Clearing aiRequiresReview is the one edit to
 * the provenance block a human is allowed to make, and routing it through PUT /api/enquiry/{id}
 * would mean opening the whole block to whatever the client happened to post back -- at which
 * point a stale form could quietly flip aiGenerated and the register would stop being able to say
 * what a machine wrote.
 */
@Data
public class AiReviewDecisionDTO {

    /** ACCEPT keeps the enquiry and clears the flag. REJECT keeps it too, but as JUNK. */
    private Decision decision;

    /** Free text logged to the conversation trail so the verdict leaves a trace. */
    private String notes;

    public enum Decision {
        /** The extraction was good. Enquiry stays, review flag clears. */
        ACCEPT,
        /** Not a real lead. Status moves to JUNK and the flag clears -- it is decided, not pending. */
        REJECT
    }
}
