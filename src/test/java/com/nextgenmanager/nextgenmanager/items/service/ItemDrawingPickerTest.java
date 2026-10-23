package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemDrawingPickerTest {

    @Test
    void theMarkedAttachmentWinsOverANameMatch() {
        FileAttachment named = file(1, "FBTM-1112.pdf", 10);
        FileAttachment marked = file(2, "scan.pdf", 1);
        marked.setPrimaryDrawing(true);

        assertThat(ItemDrawingPicker.pick(List.of(named, marked), "FBTM-1112")).contains(marked);
    }

    @Test
    void withoutAMarkAFileNamedAfterTheDrawingIsUsed_latestRevisionFirst() {
        FileAttachment revA = file(1, "FBTM-1112_RevA.pdf", 1);
        FileAttachment revB = file(2, "fbtm-1112_RevB.pdf", 2);

        assertThat(ItemDrawingPicker.pick(List.of(revB, revA), "FBTM-1112")).contains(revB);
    }

    @Test
    void anUnmarkedFileNotNamedAfterTheDrawingIsNeverGuessed() {
        // The item's only PDF, but nothing says it is the drawing.
        assertThat(ItemDrawingPicker.pick(List.of(file(1, "datasheet.pdf", 1)), "FBTM-1112")).isEmpty();
        assertThat(ItemDrawingPicker.pick(List.of(file(1, "datasheet.pdf", 1)), null)).isEmpty();
    }

    @Test
    void aShorterNumberDoesNotMatchALongerOne() {
        assertThat(ItemDrawingPicker.pick(List.of(file(1, "FBTM-1112.pdf", 1)), "FBTM-11")).isEmpty();
        assertThat(ItemDrawingPicker.pick(List.of(file(1, "FBTM-11 GA.pdf", 1)), "FBTM-11")).isPresent();
    }

    @Test
    void aDeletedFileIsNeverPicked() {
        FileAttachment gone = file(1, "FBTM-1112.pdf", 1);
        gone.setPrimaryDrawing(true);
        gone.setDeletedDate(new Date());

        assertThat(ItemDrawingPicker.pick(List.of(gone), "FBTM-1112")).isEmpty();
    }

    private static FileAttachment file(long id, String name, long uploadedDay) {
        FileAttachment a = new FileAttachment();
        a.setId(id);
        a.setOriginalName(name);
        a.setContentType("application/pdf");
        a.setUploadedAt(new Date(uploadedDay * 86_400_000L));
        return a;
    }
}
