package com.nextgenmanager.nextgenmanager.common.service;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.function.IntFunction;

/**
 * Keeps a document on the sheets its content needs, by laying it out and counting.
 *
 * <p>Several documents pad their item table with blank rows so that a short one still looks like a
 * full page. That padding is sized for a plain header; a letterhead, a wrapped address or a long
 * remark makes the same padding push the signature onto a sheet of its own. Nothing but the laid-out
 * page knows whether it fits, so the page is laid out, and padding is given up until it does.
 */
public final class PdfPageFit {

    private PdfPageFit() {
    }

    /**
     * Renders with as many filler rows as fit without adding a sheet.
     *
     * @param preferredRows the padding the template was designed with
     * @param render        renders the document padded to the given number of rows
     */
    public static byte[] withFillerRows(int preferredRows, IntFunction<byte[]> render) {
        byte[] pdf = render.apply(preferredRows);
        if (preferredRows <= 0 || pageCount(pdf) == 1) {
            return pdf;
        }
        // More than one sheet: find out how many the content needs with no padding at all.
        byte[] unpadded = render.apply(0);
        int needed = pageCount(unpadded);
        if (pageCount(pdf) == needed) {
            return pdf;
        }
        for (int rows = preferredRows - 1; rows > 0; rows--) {
            pdf = render.apply(rows);
            if (pageCount(pdf) == needed) {
                return pdf;
            }
        }
        return unpadded;
    }

    public static int pageCount(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException("The generated PDF could not be read back", e);
        }
    }
}
