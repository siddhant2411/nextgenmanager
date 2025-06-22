package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.ItemCodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCodeMappingRepository extends JpaRepository<ItemCodeMapping, Long> {

    List<ItemCodeMapping> findByCategory(String category);

    List<ItemCodeMapping> findByCategoryIgnoreCase(String type);
}
