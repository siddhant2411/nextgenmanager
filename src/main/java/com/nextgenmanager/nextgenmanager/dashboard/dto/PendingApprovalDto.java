package com.nextgenmanager.nextgenmanager.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One document waiting on the caller, whichever approval mechanism it came from. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingApprovalDto {

    /** PURCHASE_ORDER, PURCHASE_REQUISITION, SALES_ORDER or VOUCHER. The UI routes on this. */
    private String kind;

    private Long documentId;

    private String number;

    /** Vendor, customer or requester. Null where the document has none. */
    private String party;

    private BigDecimal amount;

    private LocalDate date;

    /** The role the document is waiting on. Admins see it regardless. */
    private String requiredRole;
}
