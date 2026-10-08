package com.nextgenmanager.nextgenmanager.purchase.model;

/** Where a purchase order's goods are to be delivered. Not stored: it follows from what the order points at. */
public enum ShipToKind {

    /** The company's registered address. */
    COMPANY,

    /** One of our own plants or stores (a warehouse). */
    PLANT,

    /** Somebody else's address: a customer the vendor delivers to directly, or a job worker. */
    PARTY
}
