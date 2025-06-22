package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.ItemCodeMapping;
import com.nextgenmanager.nextgenmanager.items.repository.ItemCodeMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemCodeMappingService {

    @Autowired
    private ItemCodeMappingRepository repository;

    public String resolveCode(String category, String name) {
        List<ItemCodeMapping> mappings = repository.findByCategory(category);
        String lowerName = name.toLowerCase();

        return mappings.stream()
                .filter(m -> lowerName.contains(m.getKeyword().toLowerCase()))
                .map(ItemCodeMapping::getCode)
                .findFirst()
                .orElse("GEN");
    }
}