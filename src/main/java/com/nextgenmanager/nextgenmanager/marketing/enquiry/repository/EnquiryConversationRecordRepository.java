package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Conversation records were reachable only by loading the parent enquiry, which is why the
 * follow-up history could not be reported on. This is the read path that changes that.
 */
@Repository
public interface EnquiryConversationRecordRepository extends JpaRepository<EnquiryConversationRecord, Long> {

    /**
     * Newest first, on the date the contact happened rather than the date the row was written --
     * an imported 2026 follow-up must not sort above a note typed this morning just because the
     * import ran later. V156 backfilled conversationDate for every existing row and both the
     * service and the importer set it from here on, so NULLS LAST is a formality, not a bucket.
     */
    @Query("""
        SELECT r FROM EnquiryConversationRecord r
         WHERE r.enquiry.id = :enquiryId AND r.deletedDate IS NULL
         ORDER BY r.conversationDate DESC NULLS LAST, r.creationDate DESC, r.id DESC
        """)
    List<EnquiryConversationRecord> findActiveByEnquiryId(@Param("enquiryId") Long enquiryId);
}
