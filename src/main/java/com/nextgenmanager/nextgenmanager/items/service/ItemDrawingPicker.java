package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which of an item's attachments its drawing number links to. The item list and the BOM screens
 * all ask here, so they always open the same file.
 *
 * <ol>
 *   <li>The attachment marked as the drawing ({@link FileAttachment#isPrimaryDrawing()}).</li>
 *   <li>Otherwise a file whose name carries the drawing number as a whole token — "FBTM-1112"
 *       matches "FBTM-1112_RevB.pdf" but "FBTM-11" does not. PDF first, then the latest upload,
 *       so Rev B wins over Rev A.</li>
 * </ol>
 * Nothing else is guessed: an item whose only file is not marked and not named after the drawing
 * shows its drawing number as plain text.
 */
public final class ItemDrawingPicker {

    private ItemDrawingPicker() {
    }

    /** The drawing among these attachments, or empty when none is marked or named after it. */
    public static Optional<FileAttachment> pick(List<FileAttachment> attachments, String drawingNumber) {
        if (attachments == null) {
            return Optional.empty();
        }
        List<FileAttachment> live = attachments.stream().filter(a -> a.getDeletedDate() == null).toList();

        // At most one is marked; if older data has more, the latest upload wins.
        Optional<FileAttachment> marked = live.stream().filter(FileAttachment::isPrimaryDrawing).max(OLDEST_FIRST);
        if (marked.isPresent() || drawingNumber == null || drawingNumber.isBlank()) {
            return marked;
        }
        return live.stream()
                .filter(a -> containsToken(a.getOriginalName(), drawingNumber.trim()))
                .max(Comparator.comparing(ItemDrawingPicker::isPdf).thenComparing(OLDEST_FIRST));
    }

    /** The number appears in the name with no letter or digit running on either side of it. */
    static boolean containsToken(String name, String number) {
        if (name == null) return false;
        String haystack = name.toLowerCase(Locale.ROOT);
        String needle = number.toLowerCase(Locale.ROOT);
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            int end = at + needle.length();
            boolean cleanStart = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
            boolean cleanEnd = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (cleanStart && cleanEnd) return true;
        }
        return false;
    }

    private static boolean isPdf(FileAttachment a) {
        return "application/pdf".equalsIgnoreCase(a.getContentType())
                || (a.getOriginalName() != null && a.getOriginalName().toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    /** Oldest first, so max() is the latest upload; id breaks ties and stands in for a missing date. */
    private static final Comparator<FileAttachment> OLDEST_FIRST = Comparator
            .comparing((FileAttachment a) -> a.getUploadedAt() != null ? a.getUploadedAt() : new Date(0))
            .thenComparing(a -> a.getId() != null ? a.getId() : 0L);
}
