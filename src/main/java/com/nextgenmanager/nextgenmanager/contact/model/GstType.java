package com.nextgenmanager.nextgenmanager.contact.model;

/**
 * GST registration category of a contact — critical for ITC and RCM calculation.
 *
 * REGULAR       — standard GST taxpayer, ITC claimable on purchases
 * COMPOSITION   — composition scheme taxpayer, cannot issue tax invoice, no ITC
 * UNREGISTERED  — below threshold or exempt, RCM may apply on purchases
 * SEZ           — Special Economic Zone unit, zero-rated supply
 * EXPORT        — exporter, zero-rated with LUT/bond
 */
public enum GstType {
    REGULAR,
    COMPOSITION,
    UNREGISTERED,
    SEZ,
    EXPORT
}
