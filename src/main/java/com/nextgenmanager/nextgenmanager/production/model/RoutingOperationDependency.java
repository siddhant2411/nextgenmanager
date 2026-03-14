package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.production.enums.DependencyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a dependency between two routing operations.
 *
 * Example: Op 30 (Assembly) depends on Op 10 (Cutting) with SEQUENTIAL type,
 * meaning Assembly cannot start until Cutting is completed.
 *
 * Example: Op 20 (Painting) depends on Op 10 (Cutting) with PARALLEL_ALLOWED,
 * meaning both can run at the same time once Op 10 has started.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "RoutingOperationDependency",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"routingOperationId", "dependsOnRoutingOperationId"}
        ))
public class RoutingOperationDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The operation that HAS the dependency (the downstream operation).
     * E.g. Op 30 Assembly — "I depend on Op 10 Cutting"
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routingOperationId", nullable = false)
    private RoutingOperation routingOperation;

    /**
     * The operation that must be completed/started before this one.
     * E.g. Op 10 Cutting — "I must complete before Op 30 Assembly"
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependsOnRoutingOperationId", nullable = false)
    private RoutingOperation dependsOnRoutingOperation;

    /**
     * How this dependency is enforced.
     * SEQUENTIAL: dependsOn must be COMPLETED before this op can start.
     * PARALLEL_ALLOWED: both can run concurrently.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DependencyType dependencyType = DependencyType.SEQUENTIAL;

    /**
     * Whether this dependency is mandatory.
     * If false, the system warns but does not block the operation from starting.
     */
    @Column(nullable = false)
    private Boolean isRequired = true;
}
