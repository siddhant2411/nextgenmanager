package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment,Long> {

    @Query("SELECT f FROM FileAttachment f WHERE f.referenceType = :referenceType AND f.referenceId = :referenceId AND f.deletedDate IS NULL")
    List<FileAttachment> findByReferenceTypeAndReferenceId(@Param("referenceType") String referenceType,
                                                           @Param("referenceId") Long referenceId);
}
