package com.nextgenmanager.nextgenmanager.accounting.coa.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a bulk party-ledger sweep. Data imports run this over hundreds of
 * contacts at once, so a single bad contact reports itself in {@link #failed}
 * rather than rolling the whole run back.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkLedgerResultDto {

    /** Contacts considered. */
    private int processed;

    /** Ledgers newly created by this run. */
    private int created;

    /** Ledgers that already existed and were left alone. */
    private int existing;

    /** Contacts that could not be processed, with the reason. */
    private List<Failure> failed = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failure {
        private int contactId;
        private String companyName;
        private String reason;
    }
}
