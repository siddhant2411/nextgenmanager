package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemCodeMapping;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemCodeMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryItemCodeGenerator {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ItemCodeMappingRepository itemCodeMappingRepository;

    public String generateItemCode(InventoryItem item) {
        String productType = resolveCode("PRODUCT_TYPE", item.getName());
        String modelCode = resolveCode("MODEL_CODE", item.getName());
        String size = formatSize(item.getProductSpecification().getSize());
        String group = resolveCode("GROUP", item.getItemGroupCode());

        String prefix = String.join("-", productType, modelCode, size, group);
        List<InventoryItem> similarItems = inventoryItemRepository.findByItemCodeStartingWith(prefix);
        int nextSeq = similarItems.size() + 1;
        String sequence = String.format("%03d", nextSeq);

        return prefix + "-" + sequence;
    }

    private String resolveCode(String category, String input) {
        if (input == null) return "GEN";
        String lowerInput = input.toLowerCase();
        List<ItemCodeMapping> mappings = itemCodeMappingRepository.findByCategory(category);
        return mappings.stream()
                .filter(m -> lowerInput.contains(m.getKeyword().toLowerCase()))
                .map(ItemCodeMapping::getCode)
                .findFirst()
                .orElse("GEN");
    }

    private String formatSize(String size) {
        try {
            return String.format("%03d", Integer.parseInt(size.replaceAll("[^0-9]", "")));
        } catch (Exception e) {
            return "000";
        }
    }
}
