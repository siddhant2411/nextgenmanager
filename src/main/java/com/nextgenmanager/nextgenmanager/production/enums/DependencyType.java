package com.nextgenmanager.nextgenmanager.production.enums;

/**
 * Defines how an operation depends on another operation in a routing.
 *
 * SEQUENTIAL         - Op B can only start after Op A is fully COMPLETED.
 *                      Standard finish-to-start dependency.
 *
 * PARALLEL_ALLOWED   - Op B can run concurrently with Op A.
 *                      No wait required; both operations can be IN_PROGRESS simultaneously.
 */
public enum DependencyType {
    SEQUENTIAL,
    PARALLEL_ALLOWED
}
