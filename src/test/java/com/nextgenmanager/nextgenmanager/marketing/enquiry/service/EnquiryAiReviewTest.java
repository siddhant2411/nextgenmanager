package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.AiReviewDecisionDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryConversationRecordRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The human verdict on an enquiry the AI Lead Agent raised.
 *
 * This path exists separately from the ordinary update because clearing aiRequiresReview is the
 * one edit to the provenance block a person is allowed to make. Routing it through
 * PUT /api/enquiry/{id} would open the whole block to whatever a client posted back, at which
 * point a stale form could flip aiGenerated and the register would lose its record of what a
 * machine wrote.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnquiryAiReviewTest {

    @Mock
    EnquiryRepository enquiryRepository;

    @Mock
    EnquiryConversationRecordRepository conversationRepository;

    @InjectMocks
    EnquiryServiceImpl service;

    private Enquiry aiEnquiry;

    @BeforeEach
    void setUp() {
        aiEnquiry = new Enquiry();
        aiEnquiry.setId(42L);
        aiEnquiry.setEnqNo("ENQ/2026-27/0042");
        aiEnquiry.setStatus(EnquiryStatus.NEW);
        aiEnquiry.setAiGenerated(true);
        aiEnquiry.setAiRequiresReview(true);
        aiEnquiry.setAiConfidence(new BigDecimal("0.780"));
        aiEnquiry.setAiModel("qwen3:4b");

        when(enquiryRepository.getActiveEnquiryById(42L)).thenReturn(aiEnquiry);
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AiReviewDecisionDTO decision(AiReviewDecisionDTO.Decision verdict, String notes) {
        AiReviewDecisionDTO dto = new AiReviewDecisionDTO();
        dto.setDecision(verdict);
        dto.setNotes(notes);
        return dto;
    }

    @Test
    void acceptingClearsTheFlagAndLeavesTheEnquiryOpen() {
        Enquiry result = service.applyAiReview(42L, decision(AiReviewDecisionDTO.Decision.ACCEPT, "Genuine RFQ"));

        assertThat(result.isAiRequiresReview()).isFalse();
        assertThat(result.getStatus()).isEqualTo(EnquiryStatus.NEW);
    }

    @Test
    void acceptingDoesNotEraseTheProvenance() {
        // The flag means "waiting on a human", not "was written by a machine". A salesperson
        // agreeing with the extraction does not make the enquiry hand-typed, and a close rate
        // computed later still has to be able to separate the two.
        Enquiry result = service.applyAiReview(42L, decision(AiReviewDecisionDTO.Decision.ACCEPT, null));

        assertThat(result.isAiGenerated()).isTrue();
        assertThat(result.getAiConfidence()).isEqualByComparingTo("0.780");
        assertThat(result.getAiModel()).isEqualTo("qwen3:4b");
    }

    @Test
    void rejectingMarksItJunkRatherThanDeletingIt() {
        // A lead the agent got wrong is the only evidence of what it gets wrong. Deleting it
        // destroys the sample that would tell you the classifier needs work.
        Enquiry result = service.applyAiReview(42L, decision(AiReviewDecisionDTO.Decision.REJECT, "Supplier mailshot"));

        assertThat(result.getStatus()).isEqualTo(EnquiryStatus.JUNK);
        assertThat(result.isAiRequiresReview()).isFalse();
        assertThat(result.isAiGenerated()).isTrue();
    }

    @Test
    void theVerdictIsLoggedToTheConversationTrail() {
        service.applyAiReview(42L, decision(AiReviewDecisionDTO.Decision.ACCEPT, "Checked against the drawing"));

        ArgumentCaptor<EnquiryConversationRecord> captor =
                ArgumentCaptor.forClass(EnquiryConversationRecord.class);
        verify(conversationRepository).save(captor.capture());

        // A boolean flipping is not an audit trail. Six months on, the register still has to say
        // who accepted the lead and why.
        assertThat(captor.getValue().getConversation())
                .contains("ACCEPT")
                .contains("Checked against the drawing");
        assertThat(captor.getValue().getEnquiry()).isSameAs(aiEnquiry);
    }

    @Test
    void reviewingAHandTypedEnquiryIsRejected() {
        Enquiry manual = new Enquiry();
        manual.setId(7L);
        manual.setEnqNo("ENQ/2026-27/0007");
        manual.setAiGenerated(false);
        when(enquiryRepository.getActiveEnquiryById(7L)).thenReturn(manual);

        // A client bug, not a no-op to swallow: it means the desk is pointed at the wrong row,
        // and clearing a flag that was never set would hide that.
        assertThatThrownBy(() -> service.applyAiReview(7L, decision(AiReviewDecisionDTO.Decision.ACCEPT, null)))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ENQ/2026-27/0007");

        verify(enquiryRepository, never()).save(any(Enquiry.class));
    }

    @Test
    void aMissingDecisionIsRejected() {
        assertThatThrownBy(() -> service.applyAiReview(42L, decision(null, "no verdict")))
                .isInstanceOf(InvalidDataException.class);

        assertThatThrownBy(() -> service.applyAiReview(42L, null))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void reviewingAnUnknownEnquiryIsNotFound() {
        when(enquiryRepository.getActiveEnquiryById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.applyAiReview(999L, decision(AiReviewDecisionDTO.Decision.ACCEPT, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
