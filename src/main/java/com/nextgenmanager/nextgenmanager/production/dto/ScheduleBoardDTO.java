package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The planner board for a window of time: every resource with its working time and load, the
 * operations booked on it, and the conditions a planner has to act on.
 *
 * <p>Times are IST wall-clock, to the minute, without an offset.
 */
@Data
@NoArgsConstructor
public class ScheduleBoardDTO {

    private LocalDateTime from;
    private LocalDateTime to;
    private LocalDateTime now;

    /** Every machine, and every work centre without machines, in work-centre order. */
    private List<Resource> resources = new ArrayList<>();

    private Kpis kpis = new Kpis();

    /** Orders that will finish after their due date, latest first. */
    private List<OrderAlert> lateOrders = new ArrayList<>();
    /** Orders finishing with less than a day to spare. */
    private List<OrderAlert> atRiskOrders = new ArrayList<>();
    /** New orders not yet scheduled. */
    private List<OrderAlert> unscheduledOrders = new ArrayList<>();
    /** Operations still running after their planned end. */
    private List<Bar> overrunning = new ArrayList<>();
    /** Machines that are not active, or are down right now. */
    private List<MachineAlert> machinesDown = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class Kpis {
        /** Booked productive time over available productive time, across all resources. */
        private double loadPercent;
        private long capacityMinutes;
        private long bookedMinutes;
        private int resources;
        private int idleResources;
        private int operations;
        private int lateOrders;
        private int atRiskOrders;
        private int unscheduledOrders;
        private int machinesDown;
    }

    @Data
    @NoArgsConstructor
    public static class Resource {
        /** "M:{machineId}" or "W:{workCenterId}". */
        private String key;
        private Integer workCenterId;
        private String workCenterCode;
        private String workCenterName;
        private Long machineId;
        private String machineCode;
        private String machineName;
        private String machineStatus;
        /**
         * True for the row that holds a work centre's operations with no machine, when the work
         * centre does have machines.
         */
        private boolean unassigned;
        /** When the resource is working inside the window; anything else is closed. */
        private List<Span> openTime = new ArrayList<>();
        private List<Span> downtime = new ArrayList<>();
        private long capacityMinutes;
        private long bookedMinutes;
        private double loadPercent;
        private List<Bar> bars = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Span {
        private LocalDateTime start;
        private LocalDateTime end;
        private String label;

        public Span(LocalDateTime start, LocalDateTime end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Bar {
        private Long operationId;
        private Integer sequence;
        private String operationName;
        private String operationStatus;
        private int workOrderId;
        private String workOrderNumber;
        private String workOrderStatus;
        private String priority;
        private Integer parentWorkOrderId;
        private String parentWorkOrderNumber;
        private String itemCode;
        private String itemName;
        private String workCenterCode;
        private String machineCode;
        private LocalDateTime start;
        private LocalDateTime end;
        private LocalDateTime actualStart;
        private BigDecimal workMinutes;
        private BigDecimal plannedQuantity;
        private BigDecimal completedQuantity;
        private LocalDateTime dueBy;
        /** The order this operation belongs to will finish after its due date. */
        private boolean late;
        /** Completed, running or on hold: the scheduler keeps it where it is. */
        private boolean kept;
    }

    @Data
    @NoArgsConstructor
    public static class OrderAlert {
        private int workOrderId;
        private String workOrderNumber;
        private String status;
        private String priority;
        private String itemCode;
        private String itemName;
        private LocalDateTime dueBy;
        private LocalDateTime plannedEnd;
        /** Positive when late, negative when finishing early. */
        private long minutesLate;
    }

    @Data
    @NoArgsConstructor
    public static class MachineAlert {
        private Long machineId;
        private String machineCode;
        private String machineName;
        private String workCenterCode;
        private String status;
        private String reason;
        private LocalDateTime until;
    }
}
