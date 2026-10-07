package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import java.time.LocalDateTime;

/** Where a job was placed on a resource: first working minute to last. */
public record Slot(LocalDateTime start, LocalDateTime end) {
}
