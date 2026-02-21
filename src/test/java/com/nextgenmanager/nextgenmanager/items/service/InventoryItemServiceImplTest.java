package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.FileAttachmentRepository;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.items.mapper.InventoryItemMapper;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.repository.ItemCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryItemServiceImplTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private ItemCodeRepository itemCodeRepository;
    @Mock
    private InventoryItemCodeGenerator codeGenerator;
    @Mock
    private FileAttachmentRepository fileAttachmentRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private InventoryItemMapper inventoryItemMapper;

    @InjectMocks
    private InventoryItemServiceImpl service;

    @Test
    void addInventoryItem_generatesCodeAndUploadsAttachmentsWhenItemCodeEmpty() throws Exception {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setItemCode("");

        MultipartFile attachment = mock(MultipartFile.class);
        inventoryItem.setAttachments(List.of(attachment));

        when(codeGenerator.generateItemCode(inventoryItem)).thenReturn("PEC20260001");

        InventoryItem saved = new InventoryItem();
        saved.setInventoryItemId(10);
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(saved);

        List<FileAttachment> attachments = List.of(fileAttachment(1L, "file-1.pdf"));
        when(fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", 10L))
                .thenReturn(attachments);

        InventoryItem result = service.addInventoryItem(inventoryItem);

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getItemCode()).isEqualTo("PEC20260001");

        verify(fileStorageService).uploadFile(eq(attachment), eq("inventoryItem"), eq("inventoryItem"), eq(10L), eq("SYSTEM"));
        assertThat(result.getFileAttachments()).isEqualTo(attachments);
    }

    @Test
    void addInventoryItem_doesNotGenerateCodeWhenProvided() {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setItemCode("EXISTING-1");

        InventoryItem saved = new InventoryItem();
        saved.setInventoryItemId(11);
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(saved);
        when(fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", 11L))
                .thenReturn(List.of());

        service.addInventoryItem(inventoryItem);

        verify(codeGenerator, never()).generateItemCode(any());
    }

    @Test
    void getInventoryItem_setsFileAttachmentsFromRepository() {
        InventoryItem item = new InventoryItem();
        when(inventoryItemRepository.findByActiveId(5)).thenReturn(item);

        List<FileAttachment> attachments = List.of(fileAttachment(2L, "drawing.pdf"));
        when(fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", 5L))
                .thenReturn(attachments);

        InventoryItem result = service.getInventoryItem(5);

        assertThat(result.getFileAttachments()).isEqualTo(attachments);
    }

    @Test
    void editInventoryItem_deletesRemovedAttachmentsAndUploadsNewOnes() throws Exception {
        InventoryItem existing = new InventoryItem();
        existing.setInventoryItemId(7);
        existing.setItemCode("CODE-7");

        InventoryItem updated = new InventoryItem();
        updated.setFileAttachments(List.of(
                fileAttachment(10L, "keep.pdf")
        ));

        MultipartFile newFile = mock(MultipartFile.class);
        updated.setAttachments(List.of(newFile));

        when(inventoryItemRepository.findById(7)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(updated);

        List<FileAttachment> existingFiles = List.of(
                fileAttachment(10L, "keep.pdf"),
                fileAttachment(11L, "delete.pdf")
        );
        List<FileAttachment> updatedFiles = List.of(fileAttachment(10L, "keep.pdf"));

        when(fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", 7L))
                .thenReturn(existingFiles, updatedFiles);
        when(fileStorageService.existsInStorage(existingFiles, newFile)).thenReturn(false);

        InventoryItem result = service.editInventoryItem(7, updated);

        verify(fileStorageService).deleteAttachment(11L);
        verify(fileStorageService).uploadFile(eq(newFile), eq("inventoryItem"), eq("inventoryItem"), eq(7L), eq("SYSTEM"));
        assertThat(result.getFileAttachments()).isEqualTo(updatedFiles);
        assertThat(updated.getItemCode()).isEqualTo("CODE-7");
    }

    private static FileAttachment fileAttachment(Long id, String fileName) {
        FileAttachment fileAttachment = new FileAttachment();
        fileAttachment.setId(id);
        fileAttachment.setFileName(fileName);
        return fileAttachment;
    }
}
