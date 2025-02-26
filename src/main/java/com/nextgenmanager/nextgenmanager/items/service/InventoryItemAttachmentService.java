package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItemAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

public interface InventoryItemAttachmentService {

//    public void uploadAttachment(int inventoryItemId, MultipartFile file) throws IOException;
//
//    public byte[] getFileById(long fileId) throws IOException;

    public void saveAttachment(int inventoryItemId, InventoryItemAttachment attachment);

    public Optional<InventoryItemAttachment> getAttachmentById(Long fileId);

    public void deleteAttachment(Long fileId);
}