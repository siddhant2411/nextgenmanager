package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.FileAttachmentRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.persistence.criteria.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageServiceImpl implements FileStorageService{

    @Autowired
    private final MinioClient minioClient;
    private final String bucketName;

    @Autowired
    private FileAttachmentRepository fileAttachmentRepository;

    public FileStorageServiceImpl(MinioClient minioClient,
                              @Value("${minio.bucket.name}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    private void ensureBucketExists() throws Exception{
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    @Override
    public String uploadFile(MultipartFile file, String folder, String referenceType, Long referenceId, String uploadedBy) throws Exception {
        try {

            if (file.isEmpty()) {
                throw new IllegalArgumentException("Cannot upload empty file.");
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            String originalName = file.getOriginalFilename();
            String sanitizedFileName = originalName.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");

            String fileName =  timestamp+ "_"+sanitizedFileName  ;





            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            logger.debug("File {} saved on bucket {}",originalName,bucketName);
            FileAttachment attachment = new FileAttachment();

            attachment.setReferenceType(referenceType);
            attachment.setReferenceId(referenceId);
            attachment.setFileName(fileName);
            attachment.setOriginalName(file.getOriginalFilename());
            attachment.setFolder(folder);
            attachment.setContentType(file.getContentType());
            attachment.setSize(file.getSize());
            attachment.setUploadedBy(uploadedBy);
            attachment.setUploadedAt(new Date());
            attachment.setPresignedUrlCreationDate(new Date());
            attachment.setPresignedUrl(getFileUrl(fileName));


            fileAttachmentRepository.save(attachment);
            logger.info("File {} saved",fileName);
            return fileName;

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public GetObjectResponse downloadFile(String fileName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .build());
    }

    public void deleteAttachment(Long attachmentId) throws Exception {
        FileAttachment attachment = fileAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        // Delete from MinIO
        doesObjectExist(bucketName,attachment.getFileName());
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(attachment.getFileName())
                .build());

        logger.debug("File {} removed from bucket {}",attachment.getFileName(),bucketName);
        // Delete metadata from DB
        attachment.setDeletedDate(new Date());

        fileAttachmentRepository.save(attachment);
        logger.info("File {} removed with id: {}",attachment.getFileName(),attachment.getId());
    }


    @Override
    public String getFileUrl(String fileName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(fileName)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate file URL", e);
        }
    }

    @Override
    public GetObjectResponse downloadById(Long id ) throws Exception {
        FileAttachment attachment = fileAttachmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(attachment.getFileName())
                .build());
    }


    @Override
    public boolean existsInStorage(List<FileAttachment> existingFiles, MultipartFile file) {
        for (FileAttachment f : existingFiles) {
            if (f.getFileName().equals(file.getOriginalFilename())) {
                try {
                    boolean existsInBucket = doesObjectExist(bucketName, f.getFileName());
                    if (existsInBucket) {
                        return true;
                    } else {
                        logger.warn("File {} exists in DB but missing from bucket {}", f.getFileName(), bucketName);
                        // Optionally: cleanup orphaned DB record
                        fileAttachmentRepository.delete(f);
                    }
                } catch (Exception ex) {
                    logger.error("Error checking existence of {} in bucket {}: {}", f.getFileName(), bucketName, ex.getMessage());
                }
            }
        }
        return false;
    }

    @Override
    public boolean doesObjectExist(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            // If statObject succeeds → object exists
            return true;
        } catch (ErrorResponseException e) {
            // 404 Not Found → object does not exist
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw new RuntimeException("Error checking object existence", e);
        } catch (Exception e) {
            // Other errors
            throw new RuntimeException("Error checking object existence", e);
        }
    }


}
