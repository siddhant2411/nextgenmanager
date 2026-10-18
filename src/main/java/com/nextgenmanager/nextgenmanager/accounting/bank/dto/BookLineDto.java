package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A book entry on the bank ledger. Dr = money in (a deposit on the statement), Cr = money out. */
public record BookLineDto(Long voucherLineId, Long voucherId, String voucherNumber, String voucherType,
                          LocalDate date, String narration, BigDecimal dr, BigDecimal cr,
                          Long statementLineId, LocalDate statementDate) {}
