package com.nextgenmanager.nextgenmanager.accounting.bank.dto;

import jakarta.validation.constraints.NotNull;

public record MatchRequest(@NotNull Long statementLineId, @NotNull Long voucherLineId) {}
