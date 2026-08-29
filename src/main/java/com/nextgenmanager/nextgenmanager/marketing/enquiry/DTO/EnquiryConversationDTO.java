package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

/**
 * A single logged contact with the customer, flat enough to POST back.
 *
 * The entity carries a back-reference to its enquiry, so serialising it directly drags the whole
 * enquiry graph along; this is the shape the API speaks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryConversationDTO {

    private Long id;

    /** What was said or done -- "Mail done", "Called, asked for revised drawing". */
    private String conversation;

    private EnquiryConversationRecord.ConversationType conversationType;

    /**
     * When the contact happened. Defaults to today when a caller omits it, which is right for a
     * note typed as it occurs and wrong for imported history -- so the importer always sets it.
     */
    private LocalDate conversationDate;

    /** When the row was written. Differs from conversationDate on anything imported. */
    private Date creationDate;

    public static EnquiryConversationDTO from(EnquiryConversationRecord r) {
        return EnquiryConversationDTO.builder()
                .id(r.getId())
                .conversation(r.getConversation())
                .conversationType(r.getConversationType())
                .conversationDate(r.getConversationDate())
                .creationDate(r.getCreationDate())
                .build();
    }
}
