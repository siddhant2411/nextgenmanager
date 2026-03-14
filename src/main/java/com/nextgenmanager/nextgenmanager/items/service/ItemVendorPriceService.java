package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.dto.ItemVendorPriceDTO;
import com.nextgenmanager.nextgenmanager.items.dto.ItemVendorPriceRequestDTO;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPrice;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPriceHistory;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;

import java.util.List;
import java.util.Optional;

public interface ItemVendorPriceService {

    ItemVendorPriceDTO create(ItemVendorPriceRequestDTO request);

    ItemVendorPriceDTO update(Long id, ItemVendorPriceRequestDTO request);

    void delete(Long id);

    ItemVendorPriceDTO getById(Long id);

    /** All vendor prices for an item (both PURCHASE and JOB_WORK). */
    List<ItemVendorPriceDTO> getByItem(int itemId);

    /** All vendor prices for an item filtered by priceType. */
    List<ItemVendorPriceDTO> getByItemAndType(int itemId, PriceType priceType);

    /**
     * Best price for Make-or-Buy analysis:
     *   1. Preferred vendor (if set and valid)
     *   2. Lowest price among active entries
     * Returns empty if no entries exist.
     */
    Optional<ItemVendorPrice> getBestPrice(int itemId, PriceType priceType);

    /**
     * Marks this entry as the preferred vendor for its item+priceType,
     * clearing the flag on any previous preferred entry.
     */
    ItemVendorPriceDTO setPreferred(Long id);

    /** Full price change history for a vendor-price entry, newest first. */
    List<ItemVendorPriceHistory> getHistory(Long id);
}
