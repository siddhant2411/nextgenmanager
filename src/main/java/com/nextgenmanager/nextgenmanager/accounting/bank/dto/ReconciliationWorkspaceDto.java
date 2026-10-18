package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.time.LocalDate;
import java.util.List;

public record ReconciliationWorkspaceDto(Long ledgerId, String code, String name, LocalDate asOf, LocalDate reconcileFrom,
                                         List<StatementLineDto> unmatchedStatementLines,
                                         List<BookLineDto> unmatchedBookLines,
                                         List<StatementLineDto> recentlyMatched) {}
