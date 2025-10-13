package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItemAttachment;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemAttachmentRepository;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class InventoryItemAttachmentServiceImpl implements InventoryItemAttachmentService {

    @Autowired
    private InventoryItemAttachmentRepository attachmentRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemAttachmentServiceImpl.class);
    private static final String UPLOAD_DIR = "uploads"; // Directory to store files

//    @Override
//    public void uploadAttachment(int inventoryItemId, MultipartFile file) throws IOException {
//        logger.debug("Saving the file");
//        try {
//            InventoryItem inventoryItem = inventoryItemRepository.findById(inventoryItemId)
//                    .orElseThrow(() -> new RuntimeException("Item not found"));
//
//            // Ensure upload directory exists
//            File uploadDir = new File(UPLOAD_DIR);
//            if (!uploadDir.exists()) {
//                uploadDir.mkdirs();
//            }
//
//            // Save file to local storage
//            String filePath = UPLOAD_DIR + File.separator + System.currentTimeMillis() + "_" + file.getOriginalFilename();
//            File destinationFile = new File(filePath);
//            try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
//                fos.write(file.getBytes());
//            }
//
//            // Save file details in the database
//            InventoryItemAttachment attachment = new InventoryItemAttachment();
//            attachment.setInventoryItem(inventoryItem);
//            attachment.setFileName(file.getOriginalFilename());
//            attachment.setFileType(file.getContentType());
//            attachment.setFilePath(filePath); // Store file path instead of file data
//
//            inventoryItemAttachmentRepository.save(attachment);
//        } catch (Exception e) {
//            logger.error("Error while saving the file: {}", e.getMessage());
//            throw new RuntimeException("File upload failed", e);
//        }
//    }
//
//    @Override
//    public byte[] getFileById(long fileId) throws IOException {
//        InventoryItemAttachment attachment = inventoryItemAttachmentRepository.findById(fileId)
//                .orElseThrow(() -> new RuntimeException("File not found"));
//
//        Path path = Paths.get(attachment.getFilePath());
//        return Files.readAllBytes(path);
//    }


    public void saveAttachment(int inventoryItemId, InventoryItemAttachment attachment) {
        InventoryItem inventoryItem = inventoryItemRepository.findById(inventoryItemId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found"));

        InventoryItemAttachment saveAttachment = new InventoryItemAttachment();
        saveAttachment.setFilePath(attachment.getFilePath());
        saveAttachment.setInventoryItem(inventoryItem);
        saveAttachment.setFileName(attachment.getFileName());
        saveAttachment.setFileType(attachment.getFileType());
        saveAttachment.setUploadedDate(attachment.getUploadedDate());
//        attachment.setInventoryItem(inventoryItem);
        attachmentRepository.save(saveAttachment);
    }

    public Optional<InventoryItemAttachment> getAttachmentById(Long fileId) {
        return attachmentRepository.findById(fileId);
    }

    public void deleteAttachment(Long fileId) {
        attachmentRepository.deleteById(fileId);
    }
}
