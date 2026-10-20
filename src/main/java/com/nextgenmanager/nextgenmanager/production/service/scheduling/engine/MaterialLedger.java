package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When material can be at the floor, item by item: the supply that is coming (free stock now,
 * then open orders as they arrive) and who has already claimed it.
 *
 * <p>Supply is handed out earliest first, in the order demands are made, so two orders never
 * both count on the same stock: whoever is planned first takes it, and the next one waits for
 * what arrives after. Beyond all known supply an item can still be bought in its lead time,
 * without limit; an item with no lead time has no date at all.
 *
 * <p>The ledger is derived for each scheduling run, like the capacity ledger, so it cannot
 * drift from the stock and orders it is read from.
 */
public class MaterialLedger {

    private static final double EPSILON = 1e-9;

    /** A quantity that can be used from {@code arrives}, and where it comes from. */
    private static final class Lot {
        final LocalDateTime arrives;
        final String source;
        double left;

        Lot(LocalDateTime arrives, double quantity, String source) {
            this.arrives = arrives;
            this.left = quantity;
            this.source = source;
        }
    }

    /** What is left when, after all known supply, more can be bought. */
    private record Fallback(LocalDateTime arrives, String source) {
    }

    /**
     * When a demand can be met.
     *
     * @param readyAt when the last of it arrives; null when nothing known covers it
     * @param source  where that last part comes from, e.g. "stock" or "PO/2026-27/0031"; null
     *                when undated
     */
    public record Allocation(LocalDateTime readyAt, String source) {
        public boolean dated() {
            return readyAt != null;
        }
    }

    private final Map<Integer, List<Lot>> lots = new HashMap<>();
    private final Map<Integer, Fallback> fallbacks = new HashMap<>();

    /** Adds supply of an item, kept in arrival order. Nothing is added for a quantity of zero or less. */
    public MaterialLedger supply(int itemId, LocalDateTime arrives, double quantity, String source) {
        if (quantity <= EPSILON) return this;
        List<Lot> itemLots = lots.computeIfAbsent(itemId, k -> new ArrayList<>());
        int at = itemLots.size();
        while (at > 0 && itemLots.get(at - 1).arrives.isAfter(arrives)) at--;
        itemLots.add(at, new Lot(arrives, quantity, source));
        return this;
    }

    /** What the item can be bought in, once known supply runs out. Null means it cannot be dated. */
    public MaterialLedger fallback(int itemId, LocalDateTime arrives, String source) {
        if (arrives != null) fallbacks.put(itemId, new Fallback(arrives, source));
        return this;
    }

    public boolean knows(int itemId) {
        return lots.containsKey(itemId) || fallbacks.containsKey(itemId);
    }

    /** Claims {@code quantity} of an item, earliest supply first, and says when the last of it arrives. */
    public Allocation allocate(int itemId, double quantity) {
        if (quantity <= EPSILON) return new Allocation(null, null);

        double needed = quantity;
        LocalDateTime readyAt = null;
        String source = null;
        for (Lot lot : lots.getOrDefault(itemId, List.of())) {
            if (lot.left <= EPSILON) continue;
            double take = Math.min(lot.left, needed);
            lot.left -= take;
            needed -= take;
            readyAt = lot.arrives;
            source = lot.source;
            if (needed <= EPSILON) return new Allocation(readyAt, source);
        }
        Fallback fallback = fallbacks.get(itemId);
        if (fallback == null) return new Allocation(null, null);
        return new Allocation(fallback.arrives(), fallback.source());
    }

    /** An independent copy, to plan an order against and keep only if the whole order fits. */
    public MaterialLedger copy() {
        MaterialLedger copy = new MaterialLedger();
        lots.forEach((itemId, itemLots) -> {
            List<Lot> copied = new ArrayList<>(itemLots.size());
            for (Lot lot : itemLots) copied.add(new Lot(lot.arrives, lot.left, lot.source));
            copy.lots.put(itemId, copied);
        });
        copy.fallbacks.putAll(fallbacks);
        return copy;
    }
}
