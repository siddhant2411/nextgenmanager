package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import java.util.List;

/** The first rows of an uploaded file, so the user can point at the header row and name the columns. */
public record StatementPreviewDto(String fileName, int totalRows, int headerRow, List<String> headers, List<List<String>> rows) {}
