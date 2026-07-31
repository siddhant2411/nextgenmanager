package com.nextgenmanager.nextgenmanager.items.DTO;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.model.ProductSpecification;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The complete price and cost picture for one item, assembled at export time — nothing here is
 * persisted.
 *
 * <p>It carries every money figure the item master holds, using the same vocabulary as the Finance
 * tab of the item form:
 * <ul>
 *   <li><b>Standard cost</b> — {@code ProductFinanceSettings.standardCost}, the prime cost
 *       (material + conversion, <em>excluding</em> overhead) written by the cost roll-up.</li>
 *   <li><b>Selling cost</b> — the fully loaded cost, i.e. the active BOM's total cost including
 *       overhead. Only a manufactured item with an active BOM has one; for everything else it falls
 *       back to standard cost, and {@link #sellingCostFromBom} says which of the two you are
 *       looking at.</li>
 *   <li><b>Last purchase cost</b> — what the item last actually cost to buy.</li>
 *   <li><b>List price</b> — {@code sellingPrice}, the published price.</li>
 *   <li><b>Floor price</b> — {@code minimumSellingPrice}, the lowest price a sales person may
 *       authorise. This is frequently unconfigured, which is why the cost columns matter: they let
 *       a negotiator see the margin even when no floor has been set.</li>
 * </ul>
 *
 * <p>Derived figures are never invented from cost: when no floor is configured the row reports
 * {@link #floorMissing} and leaves both floor and discount blank rather than defaulting to a
 * cost-based floor, which would be a pricing policy decision the master has not made. Margin is the
 * one derivation, and it follows the item form exactly: (list − selling cost) ÷ list × 100.
 */
@Data
public class ItemPriceDTO {

    private int inventoryItemId;

    private String itemCode;
    private String name;
    private String uom;
    private String hsnCode;
    private String specification;

    // ── Costs ──
    /** Prime cost as stored on the item master (excludes overhead). */
    private Double standardCost;
    /** Fully loaded cost: active BOM total incl. overhead, else standard cost. */
    private Double sellingCost;
    private Double lastPurchaseCost;

    // ── Prices ──
    private Double listPrice;
    private Double floorPrice;
    private Double gstRate;
    private Double priceInclGst;

    // ── Derived ──
    /** (list − selling cost) ÷ list × 100. Null when either side is unknown. */
    private Double marginPercent;
    /** (list − floor) ÷ list × 100. Null when no usable floor is configured. */
    private Double maxDiscountPercent;

    /** Selling cost came from an active BOM (incl. overhead) rather than the stored standard cost. */
    private boolean sellingCostFromBom;

    /** No cost of any kind on the master — margin cannot be shown. */
    private boolean costMissing;

    /** No floor configured for this item — discount authority is undefined. */
    private boolean floorMissing;

    /** Floor exceeds list price — a data error worth surfacing rather than printing a negative %. */
    private boolean floorInvalid;

    public static ItemPriceDTO from(InventoryItem item) {
        ItemPriceDTO row = new ItemPriceDTO();
        row.setInventoryItemId(item.getInventoryItemId());
        row.setItemCode(item.getItemCode());
        row.setName(item.getName());
        row.setUom(item.getUom() != null ? item.getUom().name() : "");
        row.setHsnCode(item.getHsnCode() != null ? item.getHsnCode() : "");
        row.setSpecification(specificationOf(item));

        ProductFinanceSettings finance = item.getProductFinanceSettings();
        if (finance == null) {
            row.setCostMissing(true);
            row.setFloorMissing(true);
            return row;
        }

        Double list = finance.getSellingPrice();
        Double floor = finance.getMinimumSellingPrice();
        Double gst = finance.getGstRate();
        Double standard = positiveOrNull(finance.getStandardCost());

        row.setListPrice(list);
        row.setGstRate(gst);
        row.setLastPurchaseCost(positiveOrNull(finance.getLastPurchaseCost()));
        row.setStandardCost(standard);
        // Until an active BOM is costed, standard cost is the best available stand-in.
        row.setSellingCost(standard);
        row.setCostMissing(standard == null);

        if (list != null && gst != null) {
            row.setPriceInclGst(round(list * (1 + gst / 100d)));
        }

        row.recalculateMargin();

        if (floor == null || floor <= 0d) {
            row.setFloorMissing(true);
            return row;
        }

        row.setFloorPrice(floor);

        if (list == null || list <= 0d) {
            return row;
        }
        if (floor > list) {
            row.setFloorInvalid(true);
            return row;
        }
        row.setMaxDiscountPercent(round((list - floor) / list * 100d));
        return row;
    }

    /**
     * Replaces the stand-in selling cost with the fully loaded BOM cost (incl. overhead) and
     * re-derives margin off it.
     */
    public void applyBomSellingCost(double loadedCost) {
        if (loadedCost <= 0d) {
            return;
        }
        setSellingCost(round(loadedCost));
        setSellingCostFromBom(true);
        setCostMissing(false);
        recalculateMargin();
    }

    private void recalculateMargin() {
        if (listPrice == null || listPrice <= 0d || sellingCost == null) {
            setMarginPercent(null);
            return;
        }
        setMarginPercent(round((listPrice - sellingCost) / listPrice * 100d));
    }

    private static Double positiveOrNull(Double value) {
        return value != null && value > 0d ? value : null;
    }

    private static String specificationOf(InventoryItem item) {
        ProductSpecification spec = item.getProductSpecification();
        if (spec == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, spec.getDimension());
        append(sb, spec.getSize());
        append(sb, spec.getBasicMaterial());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(value.trim());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
