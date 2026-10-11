package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The price floor a sales user works within: an item's minimum selling price on the item master.
 *
 * <p>An item with no minimum configured has no floor -- nothing is derived from cost, because
 * that would be a pricing policy the master has not made. The floor is always read from the
 * database, never from the item a client sent back, so a payload cannot lower it.
 */
@Service
@RequiredArgsConstructor
public class SalesPriceFloorService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InventoryItemRepository inventoryItemRepository;

    /** The item's minimum selling price, or null when none is configured. */
    public BigDecimal floorFor(InventoryItem item) {
        if (item == null || item.getInventoryItemId() <= 0) return null;
        return inventoryItemRepository.findById(item.getInventoryItemId())
                .map(InventoryItem::getProductFinanceSettings)
                .map(ProductFinanceSettings::getMinimumSellingPrice)
                .filter(min -> min > 0d)
                .map(min -> BigDecimal.valueOf(min).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    /** What the customer actually pays per unit once the line and document discounts are taken off. */
    public static BigDecimal effectiveUnitPrice(BigDecimal pricePerUnit, BigDecimal lineDiscountPct,
                                                BigDecimal documentDiscountPct) {
        BigDecimal price = pricePerUnit != null ? pricePerUnit : BigDecimal.ZERO;
        return price.multiply(remainderAfter(lineDiscountPct))
                .multiply(remainderAfter(documentDiscountPct))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isBelow(BigDecimal effectiveUnitPrice, BigDecimal floor) {
        return floor != null && effectiveUnitPrice != null && effectiveUnitPrice.compareTo(floor) < 0;
    }

    /**
     * Whether the caller may price below the floor -- the same roles that approve a sales order.
     * A call with no user behind it has nobody to hold to the floor.
     */
    public boolean callerMayGoBelowFloor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_ADMIN") ||
                               a.getAuthority().equals("ROLE_SALES_ADMIN"));
    }

    private static BigDecimal remainderAfter(BigDecimal discountPct) {
        if (discountPct == null) return BigDecimal.ONE;
        return HUNDRED.subtract(discountPct).divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }
}
