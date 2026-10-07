package com.nextgenmanager.nextgenmanager.company.model;

/** How the top of a printed document is headed. */
public enum BrandingMode {
    /** Company name and address as text. */
    NONE,
    /** The logo beside the company text. */
    LOGO,
    /** A full-width letterhead image in place of the text header. */
    LETTERHEAD,
    /** A blank band in place of the text header, for paper that already carries a printed letterhead. */
    PREPRINTED
}
