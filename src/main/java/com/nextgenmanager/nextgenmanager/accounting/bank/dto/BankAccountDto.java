package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A ledger that can be reconciled against a bank statement, with where its reconciliation stands. */
public record BankAccountDto(Long ledgerId, String code, String name, boolean mapped, LocalDate reconcileFrom,
                             int statementLines, int unmatchedStatementLines,
                             LocalDate lastStatementDate, BigDecimal lastStatementBalance) {}
