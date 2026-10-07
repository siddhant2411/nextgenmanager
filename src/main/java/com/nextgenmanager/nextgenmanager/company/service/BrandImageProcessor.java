package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import com.nextgenmanager.nextgenmanager.company.model.BrandImageKind;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Turns an uploaded logo or letterhead into the image that is stored and printed.
 *
 * <p>Nothing the user uploads is kept as-is. The file is decoded (which is the real check that it is
 * an image — the extension and content type are only the caller's word for it), scaled down to print
 * resolution for the box it will sit in, and re-encoded. So what reaches a PDF is always a PNG or
 * JPEG of bounded size that this class wrote.
 */
public final class BrandImageProcessor {

    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    /** Guards the decode: a small file can declare an enormous canvas. */
    private static final int MAX_SOURCE_SIDE_PX = 8000;

    /** 45 × 18mm logo box at roughly 380 dpi. */
    private static final int LOGO_MAX_WIDTH_PX = 675;
    private static final int LOGO_MAX_HEIGHT_PX = 270;
    private static final int LOGO_MIN_LONG_SIDE_PX = 150;

    /** 174 × 40mm letterhead band at roughly 300 dpi. */
    private static final int LETTERHEAD_MAX_WIDTH_PX = 2055;
    private static final int LETTERHEAD_MAX_HEIGHT_PX = 472;
    private static final int LETTERHEAD_MIN_WIDTH_PX = 800;
    private static final double LETTERHEAD_MIN_ASPECT = 3.0;

    /** Past this a PNG is a photograph; it is stored as a JPEG instead. */
    private static final int PNG_SOFT_LIMIT_BYTES = 700 * 1024;

    public record Processed(byte[] data, String contentType, int widthPx, int heightPx) {
    }

    private BrandImageProcessor() {
    }

    public static Processed process(BrandImageKind kind, byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new BusinessException("The file is empty.");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new BusinessException("The image is larger than 2 MB. Export a smaller PNG or JPEG and try again.");
        }

        BufferedImage source = decode(upload);
        int width = source.getWidth();
        int height = source.getHeight();

        int maxWidth;
        int maxHeight;
        if (kind == BrandImageKind.LETTERHEAD) {
            if ((double) width / height < LETTERHEAD_MIN_ASPECT) {
                throw new BusinessException("A letterhead has to be a wide strip, at least three times as wide as it is tall. "
                        + "This image is " + width + " x " + height + " px. Upload it as a logo instead, or crop it to the header strip.");
            }
            if (width < LETTERHEAD_MIN_WIDTH_PX) {
                throw new BusinessException("The letterhead is only " + width + " px wide and would print blurred across the page. "
                        + "Upload one at least " + LETTERHEAD_MIN_WIDTH_PX + " px wide.");
            }
            maxWidth = LETTERHEAD_MAX_WIDTH_PX;
            maxHeight = LETTERHEAD_MAX_HEIGHT_PX;
        } else {
            if (Math.max(width, height) < LOGO_MIN_LONG_SIDE_PX) {
                throw new BusinessException("The logo is only " + width + " x " + height + " px and would print blurred. "
                        + "Upload one at least " + LOGO_MIN_LONG_SIDE_PX + " px on its longer side.");
            }
            maxWidth = LOGO_MAX_WIDTH_PX;
            maxHeight = LOGO_MAX_HEIGHT_PX;
        }

        double scale = Math.min(1.0, Math.min((double) maxWidth / width, (double) maxHeight / height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        boolean transparent = source.getColorModel().hasAlpha();
        BufferedImage scaled = resize(source, targetWidth, targetHeight, transparent);

        try {
            byte[] png = write(scaled, "png", null);
            if (png.length <= PNG_SOFT_LIMIT_BYTES) {
                return new Processed(png, "image/png", targetWidth, targetHeight);
            }
            BufferedImage opaque = transparent ? draw(scaled, targetWidth, targetHeight, false) : scaled;
            return new Processed(write(opaque, "jpeg", 0.9f), "image/jpeg", targetWidth, targetHeight);
        } catch (IOException e) {
            throw new BusinessException("The image could not be prepared for printing.");
        }
    }

    private static BufferedImage decode(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new BusinessException("That file is not a PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    throw new BusinessException("Only PNG and JPEG images can be used. This file is "
                            + format.toUpperCase(Locale.ROOT) + ".");
                }
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > MAX_SOURCE_SIDE_PX || reader.getHeight(0) > MAX_SOURCE_SIDE_PX) {
                    throw new BusinessException("The image is larger than " + MAX_SOURCE_SIDE_PX
                            + " px on one side. Export a smaller one and try again.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("The image could not be read. Save it as an RGB PNG or JPEG and try again.");
        }
    }

    /**
     * Halves the image until it is within reach of the target before the final draw: one big bicubic
     * step from a 4000 px original throws away most of the pixels it should be averaging.
     */
    private static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight, boolean transparent) {
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        while (width / 2 > targetWidth && height / 2 > targetHeight) {
            width /= 2;
            height /= 2;
            current = draw(current, width, height, transparent);
        }
        return draw(current, targetWidth, targetHeight, transparent);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, boolean transparent) {
        BufferedImage target = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            if (!transparent) {
                // Paper is white; a transparent logo flattened for JPEG must not come out black.
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] write(BufferedImage image, String format, Float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (quality != null) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
