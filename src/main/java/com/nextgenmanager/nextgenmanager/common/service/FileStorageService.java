package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface FileStorageService {

    public String uploadFile(MultipartFile file, String folder, String referenceType, Long referenceId, String uploadedBy) throws Exception;

        public GetObjectResponse downloadFile(String file) throws Exception;

    public void deleteAttachment(Long attachmentId) throws Exception;

    public String getFileUrl(String fileName);

    public GetObjectResponse downloadById(Long id ) throws Exception;

    public boolean existsInStorage(List<FileAttachment> existingFiles, MultipartFile file);

    public boolean doesObjectExist(String bucketName, String objectName);

    public List<FileAttachment> findAttachmentsByTypeAndId(String entityType, Long entityId);

    FileAttachment getFileById(long fileId);
}

