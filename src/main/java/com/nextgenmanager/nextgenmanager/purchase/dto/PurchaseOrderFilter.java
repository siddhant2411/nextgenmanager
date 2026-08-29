package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Every filter the purchase-order list accepts, in one object.
 *
 * <p>This replaces three loose {@code @RequestParam}s that the service consumed in an if/else
 * chain, which had two failure modes worth naming because both returned a plausible wrong answer
 * rather than an error:
 *
 * <ol>
 *   <li><b>Filters cancelled each other.</b> {@code ?vendorId=5&status=SENT} returned every PO for
 *       vendor 5 in any status, because the first branch that matched won and the rest were never
 *       looked at. The caller got a longer list than it asked for with no indication why.</li>
 *   <li><b>Anything not understood was ignored.</b> {@code ?poNumber=PO/2026-27/0007} and
 *       {@code ?from=2026-04-01} bound to nothing and silently returned the unfiltered page --
 *       page one of the whole table, read as "the seven POs matching that number".</li>
 * </ol>
 *
 * Predicates here are ANDed, and {@link #KNOWN_PARAMS} is the whitelist the controller rejects
 * against, so an unsupported filter is a 400 naming the parameter.
 */
@Data
public class PurchaseOrderFilter {

    /** Exact PO number, case-insensitive. For type-ahead use {@link #poNumberContains}. */
    private String poNumber;

    /** Substring match on PO number. For search boxes -- it cannot address one row. */
    private String poNumberContains;

    /**
     * Exact external reference. This is the lookup an importer uses to decide whether a source
     * row is already on file, so it has to be exact.
     */
    private String reference;

    /** Substring match on the external reference, e.g. every PO from one source register. */
    private String referenceContains;

    /**
     * One box, three fields: matches a substring of the PO number, the external reference or the
     * vendor's name. This is what the list screen's search input sends.
     *
     * <p>It had no server side at all until now -- the screen sent {@code ?query=} against a
     * signature that did not declare it, Spring dropped it, and the unfiltered page came back
     * looking like a result set. Typing in that box appeared to work and changed nothing.
     */
    private String query;

    private Integer vendorId;

    /** Substring match on the vendor's company name. */
    private String vendorName;

    private PurchaseOrderStatus status;
    private PurchaseOrderApprovalStatus approvalStatus;
    private PurchaseOrderType poType;

    private Long salesOrderId;

    /** Inclusive order-date range. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    /** Inclusive grand-total range, in the PO's own currency. */
    private BigDecimal minTotal;
    private BigDecimal maxTotal;

    /** Paging and sorting travel on the same query string, so they belong on the whitelist too. */
    public static final Set<String> KNOWN_PARAMS = Set.of(
            "page", "size", "sort",
            "query",
            "poNumber", "poNumberContains",
            "reference", "referenceContains",
            "vendorId", "vendorName",
            "status", "approvalStatus", "poType",
            "salesOrderId",
            "fromDate", "toDate",
            "minTotal", "maxTotal");

    /** Blanks out empty strings and rejects an inverted range. Returns this so it can be chained. */
    public PurchaseOrderFilter normalized() {
        query             = blankToNull(query);
        poNumber          = blankToNull(poNumber);
        poNumberContains  = blankToNull(poNumberContains);
        reference         = blankToNull(reference);
        referenceContains = blankToNull(referenceContains);
        vendorName        = blankToNull(vendorName);

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidDataException(
                    "fromDate (" + fromDate + ") is after toDate (" + toDate + ")");
        }
        if (minTotal != null && maxTotal != null && minTotal.compareTo(maxTotal) > 0) {
            throw new InvalidDataException(
                    "minTotal (" + minTotal + ") is greater than maxTotal (" + maxTotal + ")");
        }
        return this;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
