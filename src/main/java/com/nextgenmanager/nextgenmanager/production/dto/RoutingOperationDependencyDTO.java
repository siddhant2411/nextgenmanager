package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.DependencyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO representing a single dependency declaration between two routing operations.
 *
 * Example: Operation "Assembly" (seq 30) has a dependency on "Cutting" (seq 10)
 * with type SEQUENTIAL — meaning Assembly cannot start until Cutting is COMPLETED.
 */
@Builder
@AllArgsConstructor
@Getter
@Setter
public class RoutingOperationDependencyDTO {

    private Long id;

    /** The operation sequence that must complete/run first. */
    private Long dependsOnRoutingOperationId;

    /** Sequence number of the upstream operation — for display in UI timeline. */
    private Integer dependsOnSequenceNumber;

    /** Name of the upstream operation — for display in UI tooltips/labels. */
    private String dependsOnOperationName;

    /**
     * SEQUENTIAL: downstream op waits for upstream to be COMPLETED before starting.
     * PARALLEL_ALLOWED: both can run concurrently.
     */
    private DependencyType dependencyType;

    /**
     * Whether this dependency is hard (blocking) or soft (warning only).
     */
    private Boolean isRequired;
}
