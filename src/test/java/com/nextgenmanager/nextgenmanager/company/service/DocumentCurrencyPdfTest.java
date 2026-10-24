package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.company.BrandingFixtures.Brand;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/** A document prints the sign of the currency it was raised in, and the sign is a real glyph. */
class DocumentCurrencyPdfTest {

    /** The sign is drawn in its own face after the words around it, so read the page in reading order. */
    private static String text(String currency) throws Exception {
        byte[] pdf = BrandedDocumentsRenderTest.quotation(new Brand("text", null), currency);
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String dir = System.getProperty("branding.samples.dir");
            if (dir != null && !dir.isBlank()) {
                new File(dir).mkdirs();
                ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png", new File(dir, "quotation-" + currency + ".png"));
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    @Test
    void rupeesPrintTheRupeeSignNotAHash() throws Exception {
        String text = text("INR");
        assertThat(text).contains("Rate (₹)").contains("Total (₹)").doesNotContain("(#)");
        assertThat(text).contains("Rupees Eleven Thousand Eight Hundred Only");
    }

    @Test
    void dollarsPrintTheDollarSignAndDollarsInWords() throws Exception {
        String text = text("USD");
        assertThat(text).contains("Rate ($)").contains("Total ($)").doesNotContain("₹");
        assertThat(text).contains("US Dollars Eleven Thousand Eight Hundred Only");
    }

    @Test
    void aCodeNotOnTheListPrintsAsItself() throws Exception {
        assertThat(text("thb")).contains("Rate (THB)").contains("THB Eleven Thousand Eight Hundred Only");
    }
}
