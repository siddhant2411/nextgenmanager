package com.nextgenmanager.nextgenmanager.accounting.bank.service;

import com.nextgenmanager.nextgenmanager.accounting.bank.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface BankReconciliationService {

    List<BankAccountDto> accounts();

    /** The saved mapping, or an unsaved blank one ({@code saved=false}) for an account not yet set up. */
    BankStatementMappingDto getMapping(Long ledgerId);

    BankStatementMappingDto saveMapping(Long ledgerId, BankStatementMappingDto dto);

    /** First rows of a file, so the header row and columns can be picked before anything is imported. */
    StatementPreviewDto preview(MultipartFile file, int headerRow);

    /** Imports with the saved mapping, skips rows already imported, then auto-matches. */
    StatementImportResultDto importStatement(Long ledgerId, MultipartFile file, String username);

    ReconciliationWorkspaceDto workspace(Long ledgerId, LocalDate asOf);

    /** Matches each unmatched statement line to the one book entry of the same amount within a week, where only one fits. */
    int autoMatch(Long ledgerId, String username);

    void match(Long statementLineId, Long voucherLineId, String username);

    void unmatch(Long statementLineId);

    /** Removes an imported line that is not matched (e.g. a row imported by mistake). */
    void deleteStatementLine(Long statementLineId);

    BankReconciliationStatementDto statement(Long ledgerId, LocalDate asOf);
}
