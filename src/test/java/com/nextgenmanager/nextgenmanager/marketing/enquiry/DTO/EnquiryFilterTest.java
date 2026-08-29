package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnquiryFilterTest {

    @Test
    void missingComparatorsDefaultToEquals() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqDate(LocalDate.of(2026, 3, 1));

        filter.normalized();

        // The old query switched on the comparator inside a CASE with no ELSE, so a null here
        // produced NULL, failed the enclosing AND and returned zero rows for every date filter.
        assertThat(filter.getEnqDateComp()).isEqualTo("=");
        assertThat(filter.getLastContactedDateComp()).isEqualTo("=");
        assertThat(filter.getClosedDateComp()).isEqualTo("=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=", "!=", "<", "<=", ">", ">="})
    void allSixComparatorsAreAccepted(String comparator) {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqDateComp(comparator);

        assertThat(filter.normalized().getEnqDateComp()).isEqualTo(comparator);
    }

    @Test
    void sqlStyleNotEqualsIsAcceptedAsAnAlias() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqDateComp("<>");

        assertThat(filter.normalized().getEnqDateComp()).isEqualTo("!=");
    }

    @Test
    void unknownComparatorIsRejectedRatherThanSilentlyEmptyingTheResult() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setClosedDateComp("=>");

        assertThatThrownBy(filter::normalized)
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("closedDateComp");
    }

    @Test
    void blankStringsBecomeNullSoAnEmptyFilterBoxDoesNotMatchNothing() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqNo("");
        filter.setCompanyName("   ");
        filter.setCloseReasonCode("");
        filter.setEnquirySource("  ");

        filter.normalized();

        // enqNo is an exact match now. Left as "", it would match no enquiry at all rather than
        // meaning "no filter" -- which is what the UI sends for an untouched filter box.
        assertThat(filter.getEnqNo()).isNull();
        assertThat(filter.getCompanyName()).isNull();
        assertThat(filter.getCloseReasonCode()).isNull();
        assertThat(filter.getEnquirySource()).isNull();
    }

    @Test
    void surroundingWhitespaceIsTrimmedFromTextFilters() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqNo("  41-A  ");

        assertThat(filter.normalized().getEnqNo()).isEqualTo("41-A");
    }

    @Test
    void invertedDateRangeIsRejected() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqDateFrom(LocalDate.of(2026, 6, 1));
        filter.setEnqDateTo(LocalDate.of(2026, 3, 1));

        assertThatThrownBy(filter::normalized)
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("enqDateFrom");
    }

    @Test
    void aValidRangeSurvivesNormalisation() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqDateFrom(LocalDate.of(2026, 3, 1));
        filter.setEnqDateTo(LocalDate.of(2026, 6, 1));

        filter.normalized();

        assertThat(filter.getEnqDateFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(filter.getEnqDateTo()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void normalisationIsIdempotent() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setEnqNo(" 41 ");
        filter.setStatus(EnquiryStatus.CLOSED);

        filter.normalized().normalized();

        assertThat(filter.getEnqNo()).isEqualTo("41");
        assertThat(filter.getStatus()).isEqualTo(EnquiryStatus.CLOSED);
        assertThat(filter.getEnqDateComp()).isEqualTo("=");
    }

    @Test
    void everyBindableFieldIsCoveredByTheUnknownParameterWhitelist() {
        // The whitelist is what the controller rejects against. A field added to this DTO but
        // forgotten there would be bound by Spring and then refused by the controller as unknown,
        // which is a worse failure than the silent-ignore bug it replaced.
        java.util.Set<String> declared = java.util.Arrays.stream(EnquiryFilter.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(EnquiryFilter.KNOWN_PARAMS).containsAll(declared);
    }

    // ---------------------------------------------------------------- AI Lead Agent (V160)

    @Test
    void aiProvenanceParametersAreOnTheWhitelist() {
        // The controller rejects any parameter not named here, so an omission is not a missing
        // feature -- it is a 400 on every dedupe lookup the agent makes.
        assertThat(EnquiryFilter.KNOWN_PARAMS)
                .contains("aiGenerated", "aiRequiresReview", "gmailThreadId", "gmailMessageId");
    }

    @Test
    void blankGmailIdentifiersNormaliseToNull() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setGmailThreadId("   ");
        filter.setGmailMessageId("");

        filter.normalized();

        // A blank must mean "no filter", not "match the empty string" -- the latter silently
        // returns nothing and the agent would read that as "not a duplicate" and file again.
        assertThat(filter.getGmailThreadId()).isNull();
        assertThat(filter.getGmailMessageId()).isNull();
    }

    @Test
    void gmailIdentifiersAreTrimmedRatherThanDropped() {
        EnquiryFilter filter = new EnquiryFilter();
        filter.setGmailThreadId("  18f2c9a1b3  ");

        assertThat(filter.normalized().getGmailThreadId()).isEqualTo("18f2c9a1b3");
    }

    @Test
    void aiFlagsStayNullWhenUnset() {
        // Null means "either", which is what the register wants by default. Defaulting these to
        // false would quietly hide every AI-raised enquiry from the unfiltered list.
        EnquiryFilter filter = new EnquiryFilter().normalized();

        assertThat(filter.getAiGenerated()).isNull();
        assertThat(filter.getAiRequiresReview()).isNull();
    }
}
