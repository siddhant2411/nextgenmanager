package com.nextgenmanager.nextgenmanager.production.enums;

public enum CostType {
    /** (machineRate + laborRate) × time × (1 + overhead%). Time-based / hourly. */
    CALCULATED,
    /** Flat cost per produced unit, typed directly on the operation. */
    FIXED_RATE,
    /** Outsourced operation, costed as a flat per-unit charge. */
    SUB_CONTRACTED,
    /**
     * Piece-rate: cost = rate-per-each × quantity, where the rate comes from the linked
     * {@code ProductionJob.defaultPieceRate} (or an operation-level override) and the quantity
     * (holes, kg, tests, …) is set per BOM on the routing operation. Neither hourly nor flat.
     */
    RATE_TIMES_QTY
}
