package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.TestTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestTemplateRepository extends JpaRepository<TestTemplate, Long> {

    List<TestTemplate> findByInventoryItemInventoryItemIdAndActiveTrueAndDeletedDateIsNullOrderBySequence(int itemId);

    List<TestTemplate> findByInventoryItemInventoryItemIdAndDeletedDateIsNullOrderBySequence(int itemId);
}
