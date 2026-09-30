package com.nextgenmanager.nextgenmanager.quality.model;

/** Where goods can be stopped and looked at. Each source names the document being inspected. */
public enum InspectionSource {

    /** Goods arriving on a GRN, before they join free stock. */
    INCOMING,

    /**
     * A work order operation. These lots carry no results of their own: the operator's
     * measurements already live on the operation as WorkOrderQaResult rows, and a second place to
     * enter them is how one inspection becomes two disagreeing records.
     */
    IN_PROCESS,

    /** Finished goods at work-order completion, before they are produced into stock. */
    FINAL,

    /** A packed box, before the packing slip closes. */
    PACKAGE
}
