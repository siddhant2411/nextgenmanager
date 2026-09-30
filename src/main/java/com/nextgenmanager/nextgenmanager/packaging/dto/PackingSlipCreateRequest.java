package com.nextgenmanager.nextgenmanager.packaging.dto;

/** Opens a packing slip against a confirmed (PICKED) pick list. */
public record PackingSlipCreateRequest(
        Long pickListId,
        String remarks
) {}
