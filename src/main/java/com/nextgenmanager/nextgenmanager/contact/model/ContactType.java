package com.nextgenmanager.nextgenmanager.contact.model;

/**
 * Classifies a contact's business relationship.
 *
 * VENDOR   — supplier, job-worker, or service provider (feeds procurement)
 * CUSTOMER — buyer of your finished goods (feeds sales)
 * BOTH     — acts as both vendor and customer (common in Indian MSME trade circles)
 */
public enum ContactType {
    VENDOR,
    CUSTOMER,
    BOTH
}
