package com.nextgenmanager.nextgenmanager.Inventory.model;

/**
 * What a warehouse is for. This drives routing, not reporting — stock is moved somewhere
 * because of its type, so the values are behavioural rather than descriptive labels.
 */
public enum WarehouseType {

    /** No special handling. The default for a plain store. */
    GENERAL,

    RAW_MATERIAL,

    /** Work in progress: material issued to the shop floor but not yet turned into output. */
    WIP,

    FINISHED_GOODS,

    /** Receives GRN-rejected quantity and anything failing inspection. Never picked from. */
    QUARANTINE,

    /** Receives write-offs and scrap so they leave usable stock without leaving the books. */
    SCRAP
}
