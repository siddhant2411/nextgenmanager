package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemVendorPriceHistoryRepository extends JpaRepository<ItemVendorPriceHistory, Long> {

    List<ItemVendorPriceHistory> findByItemVendorPrice_IdOrderByChangedDateDesc(Long itemVendorPriceId);
}
