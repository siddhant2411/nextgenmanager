package com.nextgenmanager.nextgenmanager.common.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.InputStream;

/**
 * Embeds DejaVu Sans as "Currency Signs", for a currency sign wrapped in <span class="cur">.
 *
 * <p>The text is set in the PDF's built-in Helvetica, which has no rupee sign: every "₹" came out as
 * "#". Only the sign is set in DejaVu. Making DejaVu the face of the page, or even a fallback in the
 * page's font list, changes the line metrics everywhere and pushes full invoices onto a second sheet.
 * Only the glyphs used are embedded.
 */
public final class PdfFonts {

    private static final String FAMILY = "Currency Signs";

    private PdfFonts() {
    }

    public static PdfRendererBuilder register(PdfRendererBuilder builder) {
        add(builder, "DejaVuSans.ttf", 400, FontStyle.NORMAL);
        add(builder, "DejaVuSans-Bold.ttf", 700, FontStyle.NORMAL);
        add(builder, "DejaVuSans-Oblique.ttf", 400, FontStyle.ITALIC);
        add(builder, "DejaVuSans-BoldOblique.ttf", 700, FontStyle.ITALIC);
        return builder;
    }

    private static void add(PdfRendererBuilder builder, String file, int weight, FontStyle style) {
        builder.useFont(() -> {
            InputStream in = PdfFonts.class.getResourceAsStream("/fonts/" + file);
            if (in == null) throw new IllegalStateException("Font missing from the build: fonts/" + file);
            return in;
        }, FAMILY, weight, style, true);
    }
}
