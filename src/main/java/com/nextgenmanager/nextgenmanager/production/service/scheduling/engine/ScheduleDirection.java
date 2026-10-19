package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

public enum ScheduleDirection {

    /** As soon as possible: each operation in the earliest slot after its predecessors. */
    FORWARD,

    /**
     * As late as possible: the last operation finishes by the due date and every earlier one is
     * pulled back from there, so material and capacity are not tied up before they are needed.
     * Falls back to forward when there is no due date or the order cannot finish on time.
     */
    BACKWARD
}
