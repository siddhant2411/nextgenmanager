package com.nextgenmanager.nextgenmanager.marketing.enquiry.controller;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryCloseReasonDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseReason;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryCloseReasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnquiryCloseReasonControllerTest {

    @Mock
    private EnquiryCloseReasonRepository repository;

    @InjectMocks
    private EnquiryCloseReasonController controller;

    private static EnquiryCloseReason reason(Long id, String code, EnquiryCloseOutcome outcome, boolean active) {
        EnquiryCloseReason r = new EnquiryCloseReason();
        r.setId(id);
        r.setCode(code);
        r.setDescription(code + " description");
        r.setOutcome(outcome);
        r.setDisplayOrder(10);
        r.setIsActive(active);
        return r;
    }

    @Test
    void getActive_returnsOnlyActiveReasonsMappedToDto() {
        when(repository.findByIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(reason(1L, "LOST_PRICE", EnquiryCloseOutcome.LOST, true)));

        ResponseEntity<List<EnquiryCloseReasonDTO>> response = controller.getActive();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).singleElement().satisfies(dto -> {
            assertThat(dto.getCode()).isEqualTo("LOST_PRICE");
            assertThat(dto.getOutcome()).isEqualTo(EnquiryCloseOutcome.LOST);
            assertThat(dto.getIsActive()).isTrue();
        });
    }

    @Test
    void getAll_includesDeactivatedReasonsSoHistoricEnquiriesStillExplain() {
        when(repository.findAll()).thenReturn(List.of(
                reason(1L, "LOST_PRICE", EnquiryCloseOutcome.LOST, true),
                reason(2L, "RETIRED_CODE", EnquiryCloseOutcome.DECLINED_BY_US, false)));

        ResponseEntity<List<EnquiryCloseReasonDTO>> response = controller.getAll();

        assertThat(response.getBody()).extracting(EnquiryCloseReasonDTO::getCode)
                .containsExactly("LOST_PRICE", "RETIRED_CODE");
    }

    @Test
    void create_rejectsBlankCode() {
        ResponseEntity<?> response = controller.create(EnquiryCloseReasonDTO.builder().code("  ").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateCodeWithConflict() {
        when(repository.findByCodeIgnoreCase("LOST_PRICE"))
                .thenReturn(Optional.of(reason(1L, "LOST_PRICE", EnquiryCloseOutcome.LOST, true)));

        ResponseEntity<?> response = controller.create(
                EnquiryCloseReasonDTO.builder().code("lost_price").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    void create_normalisesCodeToUpperCaseAndAppliesEntityDefaults() {
        when(repository.findByCodeIgnoreCase("NEW_REASON")).thenReturn(Optional.empty());
        when(repository.save(any(EnquiryCloseReason.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> response = controller.create(
                EnquiryCloseReasonDTO.builder().code(" new_reason ").description("Something").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<EnquiryCloseReason> saved = ArgumentCaptor.forClass(EnquiryCloseReason.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("NEW_REASON");
        assertThat(saved.getValue().getDescription()).isEqualTo("Something");
        // Nothing was supplied for these, so the entity's own defaults have to survive the request.
        assertThat(saved.getValue().getOutcome()).isEqualTo(EnquiryCloseOutcome.LOST);
        assertThat(saved.getValue().getDisplayOrder()).isEqualTo(100);
        assertThat(saved.getValue().getIsActive()).isTrue();
    }

    @Test
    void update_appliesOnlyTheFieldsSupplied() {
        EnquiryCloseReason existing = reason(5L, "NO_RESPONSE", EnquiryCloseOutcome.NO_ENGAGEMENT, true);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(EnquiryCloseReason.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> response = controller.update(5L,
                EnquiryCloseReasonDTO.builder().displayOrder(42).build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getDisplayOrder()).isEqualTo(42);
        // A partial update must not blank out what it did not mention.
        assertThat(existing.getDescription()).isEqualTo("NO_RESPONSE description");
        assertThat(existing.getOutcome()).isEqualTo(EnquiryCloseOutcome.NO_ENGAGEMENT);
    }

    @Test
    void update_neverChangesTheCode() {
        EnquiryCloseReason existing = reason(5L, "NO_RESPONSE", EnquiryCloseOutcome.NO_ENGAGEMENT, true);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(EnquiryCloseReason.class))).thenAnswer(i -> i.getArgument(0));

        controller.update(5L, EnquiryCloseReasonDTO.builder().code("SOMETHING_ELSE").build());

        // The code is what every historic enquiry reports under; renaming it in place would
        // restate closed periods rather than correct them.
        assertThat(existing.getCode()).isEqualTo("NO_RESPONSE");
    }

    @Test
    void update_returns404ForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.update(99L, EnquiryCloseReasonDTO.builder().build()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deactivate_flagsInactiveAndDoesNotDelete() {
        EnquiryCloseReason existing = reason(7L, "TENDER_MISSED", EnquiryCloseOutcome.DECLINED_BY_US, true);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = controller.deactivate(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getIsActive()).isFalse();
        verify(repository).save(existing);
        // Enquiries closed under this reason still point at it; deleting it would orphan them.
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deactivate_returns404ForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.deactivate(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
