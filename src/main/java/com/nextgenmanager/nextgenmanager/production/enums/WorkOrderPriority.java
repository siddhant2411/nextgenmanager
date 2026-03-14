package com.nextgenmanager.nextgenmanager.production.enums;

public enum WorkOrderPriority {
    URGENT(1),
    HIGH(2),
    NORMAL(3),
    LOW(4);

    private final int rank;

    WorkOrderPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
