package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bank reconciliation statement as at a date. Balance as per bank (computed) =
 * book balance − deposits not yet credited + payments not yet debited
 * + bank credits not in the books − bank debits not in the books.
 * It ties when that equals the balance printed on the statement.
 */
public record BankReconciliationStatementDto(
        Long ledgerId, String code, String name, LocalDate asOf, LocalDate reconcileFrom,
        BigDecimal bookBalance,
        List<BookLineDto> depositsNotCredited, BigDecimal depositsNotCreditedTotal,
        List<BookLineDto> paymentsNotDebited, BigDecimal paymentsNotDebitedTotal,
        List<StatementLineDto> bankCreditsNotInBooks, BigDecimal bankCreditsNotInBooksTotal,
        List<StatementLineDto> bankDebitsNotInBooks, BigDecimal bankDebitsNotInBooksTotal,
        BigDecimal computedBankBalance,
        BigDecimal statementBalance, LocalDate statementBalanceDate,
        BigDecimal difference, boolean tiesOut) {}
