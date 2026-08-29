package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryPriority;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Set;

/**
 * Every filter the enquiry list and export accept, in one object.
 *
 * Two rules this type exists to enforce:
 *
 * 1. A filter that is not understood is an error, not a no-op. Spring binds query parameters by
 *    name and silently drops the rest, so `?status=CLOSED` against the old signature returned
 *    every row -- a KPI tile would have shown a wrong number under a correct-looking label.
 *    {@link #KNOWN_PARAMS} is the whitelist the controller rejects against.
 *
 * 2. A date comparator is validated up front. The old native query switched on it inside a SQL
 *    CASE with no ELSE, so a null or misspelled comparator evaluated to NULL, failed the
 *    enclosing AND and dropped every row -- every date filter silently returned nothing.
 */
@Data
public class EnquiryFilter {

    /** Exact enquiry number, case-insensitive. For type-ahead use {@link #enqNoContains}. */
    private String enqNo;

    /** Substring match on enquiry number. Only for search boxes -- it cannot address one row. */
    private String enqNoContains;

    /** Substring match across the linked contact's name and the free-text company name. */
    private String companyName;

    private EnquiryStatus status;
    private EnquiryPriority priority;

    /** Commercial outcome of the close reason -- WON / LOST / NO_ENGAGEMENT / ... */
    private EnquiryCloseOutcome outcome;

    /** Exact close-reason code, e.g. NO_RESPONSE. */
    private String closeReasonCode;

    /** Substring match on the enquiry source, e.g. IndiaMart. */
    private String enquirySource;

    private Long assignedToId;
    private Integer daysForNextFollowup;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lastContactedDate;
    private String lastContactedDateComp;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate enqDate;
    private String enqDateComp;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate closedDate;
    private String closedDateComp;

    /** Inclusive enquiry-date range. A range is the one thing a single comparator cannot express. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate enqDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate enqDateTo;

    /** Rows the AI Lead Agent wrote. Null means both, which is what the register normally wants. */
    private Boolean aiGenerated;

    /** The review desk's whole query: agent-written rows still waiting on a human. */
    private Boolean aiRequiresReview;

    /** Exact Gmail thread. The agent's dedupe lookup -- a substring match would be wrong here. */
    private String gmailThreadId;

    /** Exact Gmail message. Unique where present, so this addresses at most one row. */
    private String gmailMessageId;

    public static final String DEFAULT_COMPARATOR = "=";

    public static final Set<String> COMPARATORS = Set.of("=", "!=", "<", "<=", ">", ">=");

    /** Paging and sorting parameters travel on the same query string, so they belong here too. */
    public static final Set<String> KNOWN_PARAMS = Set.of(
            "page", "size", "sortBy", "sortDir",
            "enqNo", "enqNoContains", "companyName", "status", "priority", "outcome",
            "closeReasonCode", "enquirySource", "assignedToId", "daysForNextFollowup",
            "lastContactedDate", "lastContactedDateComp",
            "enqDate", "enqDateComp",
            "closedDate", "closedDateComp",
            "enqDateFrom", "enqDateTo",
            "aiGenerated", "aiRequiresReview", "gmailThreadId", "gmailMessageId");

    /**
     * Blanks out empty strings, defaults missing comparators and rejects unusable ones.
     * Returns this so it can be chained at the call site.
     */
    public EnquiryFilter normalized() {
        enqNo = blankToNull(enqNo);
        enqNoContains = blankToNull(enqNoContains);
        companyName = blankToNull(companyName);
        closeReasonCode = blankToNull(closeReasonCode);
        enquirySource = blankToNull(enquirySource);
        gmailThreadId = blankToNull(gmailThreadId);
        gmailMessageId = blankToNull(gmailMessageId);

        lastContactedDateComp = comparator(lastContactedDateComp, "lastContactedDateComp");
        enqDateComp = comparator(enqDateComp, "enqDateComp");
        closedDateComp = comparator(closedDateComp, "closedDateComp");

        if (enqDateFrom != null && enqDateTo != null && enqDateFrom.isAfter(enqDateTo)) {
            throw new InvalidDataException(
                    "enqDateFrom (" + enqDateFrom + ") is after enqDateTo (" + enqDateTo + ")");
        }
        return this;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String comparator(String raw, String paramName) {
        if (raw == null || raw.isBlank()) return DEFAULT_COMPARATOR;
        String v = raw.trim();
        if (v.equals("<>")) return "!=";
        if (!COMPARATORS.contains(v)) {
            throw new InvalidDataException(
                    paramName + " must be one of " + COMPARATORS + " but was '" + raw + "'");
        }
        return v;
    }
}
