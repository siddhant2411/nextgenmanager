package com.nextgenmanager.nextgenmanager.bom.repository;

import com.nextgenmanager.nextgenmanager.bom.model.BomAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BomAttachmentRepository extends JpaRepository<BomAttachment, Long> {

    @Query("SELECT b FROM BomAttachment b WHERE b.fileName LIKE CONCAT(:prefix, '_v%.', :extension)")
    List<BomAttachment> findAllVersionsByPrefixAndExtension(@Param("prefix") String prefix, @Param("extension") String extension);

    @Modifying
    @Query("DELETE FROM BomAttachment b WHERE b.fileName LIKE CONCAT(:prefix, '_v%.', :extension)")
    void deleteAllVersionsByPrefixAndExtension(@Param("prefix") String prefix, @Param("extension") String extension);



}
