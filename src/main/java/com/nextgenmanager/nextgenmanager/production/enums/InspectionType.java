package com.nextgenmanager.nextgenmanager.production.enums;

public enum InspectionType {
    VISUAL,           // Operator eye-check (surface finish, weld quality)
    DIMENSIONAL,      // Caliper / gauge measurement
    FUNCTIONAL,       // Does the part work? (e.g., motor runs, valve seals)
    MATERIAL_TEST,    // Hardness, tensile strength, chemical composition
    WEIGHT_CHECK,     // Weigh on scale
    PRESSURE_TEST,    // Pressure/leak test
    ELECTRICAL_TEST   // Continuity, insulation, voltage
}
