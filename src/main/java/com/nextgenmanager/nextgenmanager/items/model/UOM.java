package com.nextgenmanager.nextgenmanager.items.model;

public enum UOM {
    NOS,
    KG,
    GRAM,
    TON,
    METER,
    CENTIMETER,
    INCH,
    LITER,
    SET,
    // Area units — appended at the end so existing ordinals (0..8) are preserved.
    // inventoryItem.uom is persisted as an enum ORDINAL; never reorder or insert mid-list.
    // See migration V149 which widens the inventoryitem_uom_check constraint to 0..11.
    SQFT,
    SQM,
    SQIN
}
