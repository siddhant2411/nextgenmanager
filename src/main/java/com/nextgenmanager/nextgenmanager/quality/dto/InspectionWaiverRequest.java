package com.nextgenmanager.nextgenmanager.quality.dto;

/** Releasing goods that failed. The reason is required — a waiver with no stated reason is a gap. */
public record InspectionWaiverRequest(String waivedBy, String reason) {}
