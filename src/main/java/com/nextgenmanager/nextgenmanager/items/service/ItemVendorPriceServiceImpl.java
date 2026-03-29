package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.dto.ItemVendorPriceDTO;
import com.nextgenmanager.nextgenmanager.items.dto.ItemVendorPriceRequestDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPrice;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPriceHistory;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemVendorPriceHistoryRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemVendorPriceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ItemVendorPriceServiceImpl implements ItemVendorPriceService {

    @Autowired
    private ItemVendorPriceRepository repository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ItemVendorPriceHistoryRepository historyRepository;

    // ──────────────────────────── CRUD ────────────────────────────

    @Override
    public ItemVendorPriceDTO create(ItemVendorPriceRequestDTO request) {
        InventoryItem item = inventoryItemRepository.findByActiveId(request.getInventoryItemId());
        if (item == null) throw new EntityNotFoundException("Item not found: " + request.getInventoryItemId());

        Contact vendor = contactRepository.findById(request.getVendorId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + request.getVendorId()));

        if (repository.existsByInventoryItem_InventoryItemIdAndVendor_IdAndPriceTypeAndDeletedDateIsNull(
                request.getInventoryItemId(), request.getVendorId(), request.getPriceType())) {
            throw new IllegalStateException(
                    "A price entry already exists for this item + vendor + priceType combination. Use update instead.");
        }

        if (request.isPreferredVendor()) {
            clearExistingPreferred(request.getInventoryItemId(), request.getPriceType());
        }

        ItemVendorPrice entity = new ItemVendorPrice();
        mapRequestToEntity(request, entity, item, vendor);
        return toDTO(repository.save(entity));
    }

    @Override
    public ItemVendorPriceDTO update(Long id, ItemVendorPriceRequestDTO request) {
        ItemVendorPrice entity = getActiveEntity(id);

        if (request.isPreferredVendor() && !entity.isPreferredVendor()) {
            clearExistingPreferred(entity.getInventoryItem().getInventoryItemId(), entity.getPriceType());
        }

        // Log price history when price actually changes
        BigDecimal oldPrice = entity.getPricePerUnit();
        if (oldPrice.compareTo(request.getPricePerUnit()) != 0) {
            logPriceHistory(entity, oldPrice, request.getPricePerUnit());
        }

        // Only price, validity and metadata are updatable (item+vendor+type are immutable)
        entity.setPricePerUnit(request.getPricePerUnit());
        entity.setLastQuotedDate(new Date());
        entity.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        entity.setLeadTimeDays(request.getLeadTimeDays());
        entity.setMinimumOrderQuantity(request.getMinimumOrderQuantity());
        entity.setValidFrom(request.getValidFrom());
        entity.setValidTo(request.getValidTo());
        entity.setPreferredVendor(request.isPreferredVendor());
        entity.setGstRegistered(request.isGstRegistered());
        entity.setPaymentTerms(request.getPaymentTerms());
        entity.setRemarks(request.getRemarks());

        return toDTO(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemVendorPriceHistory> getHistory(Long id) {
        return historyRepository.findByItemVendorPrice_IdOrderByChangedDateDesc(id);
    }

    @Override
    public void delete(Long id) {
        ItemVendorPrice entity = getActiveEntity(id);
        entity.setDeletedDate(new Date());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemVendorPriceDTO getById(Long id) {
        return toDTO(getActiveEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemVendorPriceDTO> getByItem(int itemId) {
        return repository.findByInventoryItem_InventoryItemIdAndDeletedDateIsNull(itemId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemVendorPriceDTO> getByItemAndType(int itemId, PriceType priceType) {
        return repository.findByInventoryItem_InventoryItemIdAndPriceTypeAndDeletedDateIsNull(itemId, priceType)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ──────────────────────────── preferred ────────────────────────────

    @Override
    public ItemVendorPriceDTO setPreferred(Long id) {
        ItemVendorPrice entity = getActiveEntity(id);
        clearExistingPreferred(entity.getInventoryItem().getInventoryItemId(), entity.getPriceType());
        entity.setPreferredVendor(true);
        return toDTO(repository.save(entity));
    }

    // ──────────────────────────── analysis helper ────────────────────────────

    /**
     * Used by MakeBuyAnalysisService.
     * Priority: preferred vendor → lowest price among valid entries.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ItemVendorPrice> getBestPrice(int itemId, PriceType priceType) {
        // 1. Preferred vendor
        Optional<ItemVendorPrice> preferred =
                repository.findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(
                        itemId, priceType);
        if (preferred.isPresent()) return preferred;

        // 2. Cheapest among all active entries
        List<ItemVendorPrice> all = repository.findCheapestByItemAndType(itemId, priceType);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    // ──────────────────────────── private helpers ────────────────────────────

    private void logPriceHistory(ItemVendorPrice entity, java.math.BigDecimal oldPrice, java.math.BigDecimal newPrice) {
        String changedBy = null;
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) changedBy = auth.getName();
        } catch (Exception ignored) {}

        ItemVendorPriceHistory history = new ItemVendorPriceHistory();
        history.setItemVendorPrice(entity);
        history.setOldPrice(oldPrice);
        history.setNewPrice(newPrice);
        history.setChangedBy(changedBy);
        historyRepository.save(history);
    }

    private void clearExistingPreferred(int itemId, PriceType priceType) {
        repository.findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(
                itemId, priceType)
                .ifPresent(existing -> {
                    existing.setPreferredVendor(false);
                    repository.save(existing);
                });
    }

    private ItemVendorPrice getActiveEntity(Long id) {
        ItemVendorPrice entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ItemVendorPrice not found: " + id));
        if (entity.getDeletedDate() != null) {
            throw new EntityNotFoundException("ItemVendorPrice not found: " + id);
        }
        return entity;
    }

    private void mapRequestToEntity(ItemVendorPriceRequestDTO req, ItemVendorPrice e,
                                    InventoryItem item, Contact vendor) {
        e.setInventoryItem(item);
        e.setVendor(vendor);
        e.setPriceType(req.getPriceType());
        e.setPricePerUnit(req.getPricePerUnit());
        e.setCurrency(req.getCurrency() != null ? req.getCurrency() : "INR");
        e.setLeadTimeDays(req.getLeadTimeDays());
        e.setMinimumOrderQuantity(req.getMinimumOrderQuantity());
        e.setValidFrom(req.getValidFrom());
        e.setValidTo(req.getValidTo());
        e.setPreferredVendor(req.isPreferredVendor());
        e.setGstRegistered(req.isGstRegistered());
        e.setPaymentTerms(req.getPaymentTerms());
        e.setRemarks(req.getRemarks());
    }

    private ItemVendorPriceDTO toDTO(ItemVendorPrice e) {
        return ItemVendorPriceDTO.builder()
                .id(e.getId())
                .inventoryItemId(e.getInventoryItem().getInventoryItemId())
                .itemCode(e.getInventoryItem().getItemCode())
                .itemName(e.getInventoryItem().getName())
                .vendorId(e.getVendor().getId())
                .vendorName(e.getVendor().getCompanyName())
                .vendorGstNumber(e.getVendor().getGstNumber())
                .priceType(e.getPriceType())
                .pricePerUnit(e.getPricePerUnit())
                .currency(e.getCurrency())
                .leadTimeDays(e.getLeadTimeDays())
                .minimumOrderQuantity(e.getMinimumOrderQuantity())
                .validFrom(e.getValidFrom())
                .validTo(e.getValidTo())
                .isPreferredVendor(e.isPreferredVendor())
                .gstRegistered(e.isGstRegistered())
                .paymentTerms(e.getPaymentTerms())
                .remarks(e.getRemarks())
                .lastQuotedDate(e.getLastQuotedDate())
                .creationDate(e.getCreationDate())
                .updatedDate(e.getUpdatedDate())
                .build();
    }
}
