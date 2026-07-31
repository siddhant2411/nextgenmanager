package com.nextgenmanager.nextgenmanager.items.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Request for a sales price list export.
 *
 * <p>Items are resolved from {@link #itemIds} when the user has picked rows explicitly, otherwise
 * from {@link #filter} (the same {@code FilterRequest} the item grid already posts to
 * {@code /filter}). When neither is supplied every priced item is exported.
 *
 * <p>{@link #audience} decides what pricing is disclosed: {@code CUSTOMER} sheets carry list price
 * and GST only, {@code INTERNAL} sheets additionally carry the full cost picture (standard cost,
 * loaded selling cost, margin) plus the floor price and the maximum discount a sales person may
 * authorise. Manufacturing cost is never exported on a customer sheet.
 */
@Data
public class PriceListExportRequest {

    /** Explicitly selected item ids. Takes precedence over {@link #filter}. */
    private List<Integer> itemIds;

    /** Grid filter to export instead of an explicit selection. */
    private FilterRequest filter;

    /** PDF or XLSX. */
    private String format = "PDF";

    /** CUSTOMER or INTERNAL. */
    private String audience = "CUSTOMER";

    /** Quote-validity deadline printed on the document. Defaults to today + 7 days. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate validUntil;

    /** Optional customer this list was prepared for. */
    private String customerName;

    /** Optional document title override. */
    private String title;

    /** Optional preparer name shown in the footer. */
    private String preparedBy;

    public boolean isInternal() {
        return "INTERNAL".equalsIgnoreCase(audience);
    }

    public boolean isExcel() {
        return "XLSX".equalsIgnoreCase(format) || "EXCEL".equalsIgnoreCase(format);
    }

    /** Validity deadline, defaulting to a week out when the caller did not choose one. */
    public LocalDate resolvedValidUntil() {
        return validUntil != null ? validUntil : LocalDate.now().plusWeeks(1);
    }

    public String resolvedTitle() {
        return title != null && !title.isBlank() ? title : "Price List";
    }
}
