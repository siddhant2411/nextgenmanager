package com.nextgenmanager.nextgenmanager.accounting.tds.repository;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TdsSectionRepository extends JpaRepository<TdsSection, Long> {

    Optional<TdsSection> findBySectionAndDeletedDateIsNull(String section);

    List<TdsSection> findByDeletedDateIsNullOrderBySectionAsc();

    List<TdsSection> findByActiveTrueAndDeletedDateIsNullOrderBySectionAsc();
}
