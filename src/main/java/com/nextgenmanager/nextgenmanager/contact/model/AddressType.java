package com.nextgenmanager.nextgenmanager.contact.model;

/**
 * Purpose of a contact address.
 *
 * BILLING  — registered address for invoice / GST purposes
 * SHIPPING — delivery / dispatch address
 * FACTORY  — physical manufacturing location (useful for job-work dispatch)
 * BOTH     — serves as both billing and shipping
 */
public enum AddressType {
    BILLING,
    SHIPPING,
    FACTORY,
    BOTH
}
