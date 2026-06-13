package com.nextgenmanager.nextgenmanager.accounting.opening.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** One row in the opening balance Excel import / manual entry. */
@Data
public class OpeningBalanceRowDto {
    @NotBlank private String ledgerCode;
    private String contactCode;  // for party sub-ledger entries (customer/vendor individual balances)
    @NotNull  private BigDecimal amount;
    @NotBlank private String drCr;  // "DR" or "CR"
}
