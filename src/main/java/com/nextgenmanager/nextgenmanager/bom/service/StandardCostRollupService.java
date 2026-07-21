package com.nextgenmanager.nextgenmanager.bom.service;

import java.math.BigDecimal;

/**
 * Multi-level standard-cost roll-up. Recomputes each manufactured item's stored standardCost as the
 * fully-loaded cost of its active BOM (material + conversion + additional + its own blanket overhead),
 * bottom-up so a parent's material lines pick up each child's already-loaded cost.
 */
public interface StandardCostRollupService {

    /**
     * Roll up every item that has an active BOM. Bottom-up, cycle-protected, shared-subtree memoized.
     * @return number of manufactured items whose standardCost was recomputed.
     */
    int rollUpAllStandardCosts();

    /**
     * Roll up a single item and its manufactured sub-tree, writing standardCost at each manufactured
     * level. Returns the item's fully-loaded unit cost.
     */
    BigDecimal rollUpItemStandardCost(int itemId);
}
