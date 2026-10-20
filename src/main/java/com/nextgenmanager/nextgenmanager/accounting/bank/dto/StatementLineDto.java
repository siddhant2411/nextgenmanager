package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code staleMatch}: the book entry it was matched to has since been reversed — it needs matching again. */
public record StatementLineDto(Long id, LocalDate txnDate, String narration, String reference,
                               BigDecimal withdrawal, BigDecimal deposit, BigDecimal balance,
                               Long voucherLineId, String voucherNumber, LocalDate voucherDate, boolean staleMatch) {}
