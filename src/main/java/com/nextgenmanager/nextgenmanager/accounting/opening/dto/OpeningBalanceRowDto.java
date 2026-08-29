package com.nextgenmanager.nextgenmanager.accounting.opening.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row in the opening balance Excel import / manual entry. */
@Data
public class OpeningBalanceRowDto {
    @NotBlank private String ledgerCode;
    private String contactCode;  // for party sub-ledger entries (customer/vendor individual balances)
    @NotNull  private BigDecimal amount;
    @NotBlank private String drCr;  // "DR" or "CR"

    // Per-bill open-item detail (V162). Optional: absent means one lump balance for the ledger.
    // billDate is the ORIGINAL document date and drives ageing, not the opening voucher's date.
    private String    billReference;
    private LocalDate billDate;
    private LocalDate dueDate;
}
