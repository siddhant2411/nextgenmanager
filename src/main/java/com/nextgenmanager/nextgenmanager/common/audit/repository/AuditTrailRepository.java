package com.nextgenmanager.nextgenmanager.common.audit.repository;

import com.nextgenmanager.nextgenmanager.common.audit.model.AuditTrailEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface AuditTrailRepository extends JpaRepository<AuditTrailEntry, Long> {

    /**
     * The register: newest first, every filter optional. {@code username} must arrive lower-cased —
     * LOWER() on a null parameter makes Postgres guess bytea and fail.
     */
    @Query("""
        SELECT a FROM AuditTrailEntry a
        WHERE a.eventTime >= :from AND a.eventTime < :to
          AND (:entityType IS NULL OR a.entityType = :entityType)
          AND (:action IS NULL OR a.action = :action)
          AND (:username IS NULL OR LOWER(a.username) = :username)
        ORDER BY a.eventTime DESC, a.id DESC
        """)
    List<AuditTrailEntry> search(@Param("from") Date from,
                                 @Param("to") Date to,
                                 @Param("entityType") String entityType,
                                 @Param("action") String action,
                                 @Param("username") String username);

    /** Everything that ever happened to one record, oldest first. */
    List<AuditTrailEntry> findByEntityTypeAndEntityIdOrderByEventTimeAscIdAsc(String entityType, Long entityId);

    List<AuditTrailEntry> findByActionOrderByEventTimeDesc(String action);
}
