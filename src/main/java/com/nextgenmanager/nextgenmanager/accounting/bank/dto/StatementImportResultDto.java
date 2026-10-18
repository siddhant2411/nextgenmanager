package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.util.List;

public record StatementImportResultDto(String importBatch, int imported, int duplicates, int autoMatched, List<String> skipped) {}
