package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnquiryCloseReasonRepository extends JpaRepository<EnquiryCloseReason, Long> {

    Optional<EnquiryCloseReason> findByCode(String code);

    Optional<EnquiryCloseReason> findByCodeIgnoreCase(String code);

    List<EnquiryCloseReason> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<EnquiryCloseReason> findByOutcomeAndIsActiveTrueOrderByDisplayOrderAsc(EnquiryCloseOutcome outcome);
}
