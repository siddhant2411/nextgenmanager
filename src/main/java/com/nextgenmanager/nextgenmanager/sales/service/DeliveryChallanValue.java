package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNoteItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The value of the goods on a delivery challan.
 *
 * <p>Rule 55 of the CGST Rules requires a challan to state the taxable value of the goods it moves
 * and, when they are moving as a supply to the consignee, the tax rate and tax amount as well. A
 * delivery note stores quantities only, so the value is worked out here from the sales order line
 * each item was sold on: the order's price, less the order's discount, for the quantity actually
 * on this challan. Freight is not goods and is left to the invoice.
 */
@Getter
public final class DeliveryChallanValue {

    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final List<Line> lines = new ArrayList<>();
    private BigDecimal taxableValue = BigDecimal.ZERO;
    private BigDecimal cgst = BigDecimal.ZERO;
    private BigDecimal sgst = BigDecimal.ZERO;
    private BigDecimal igst = BigDecimal.ZERO;
    /** False when some item on the challan is not on the order, so no honest total can be printed. */
    private boolean complete = true;

    private DeliveryChallanValue() {
    }

    public BigDecimal getTotalValue() {
        return taxableValue.add(cgst).add(sgst).add(igst);
    }

    public static DeliveryChallanValue of(DeliveryNote dn) {
        DeliveryChallanValue value = new DeliveryChallanValue();
        SalesOrder so = dn.getSalesOrder();
        boolean interState = so != null && so.getTaxType() == TaxType.IGST;
        BigDecimal discountPct = so != null ? nz(so.getDiscountPercentage()) : BigDecimal.ZERO;

        for (DeliveryNoteItem item : dn.getItems() != null ? dn.getItems() : List.<DeliveryNoteItem>of()) {
            SalesOrderItem sold = orderLineFor(so, item);
            if (sold == null || sold.getPricePerUnit() == null) {
                value.lines.add(new Line(item, null, null, null));
                value.complete = false;
                continue;
            }
            // The price the customer pays for one unit, once the order's discount is taken off.
            BigDecimal rate = sold.getPricePerUnit()
                    .multiply(HUNDRED.subtract(discountPct)).divide(HUNDRED, 2, ROUND);
            BigDecimal taxable = nz(item.getQuantityDelivered()).multiply(sold.getPricePerUnit())
                    .multiply(HUNDRED.subtract(discountPct)).divide(HUNDRED, 2, ROUND);
            BigDecimal gstRate = gstRateOf(sold, so);

            BigDecimal tax = taxable.multiply(gstRate).divide(HUNDRED, 2, ROUND);
            if (interState) {
                value.igst = value.igst.add(tax);
            } else {
                BigDecimal half = tax.divide(TWO, 2, ROUND);
                value.cgst = value.cgst.add(half);
                value.sgst = value.sgst.add(tax.subtract(half));
            }
            value.taxableValue = value.taxableValue.add(taxable);
            value.lines.add(new Line(item, rate, gstRate, taxable));
        }
        return value;
    }

    /** A delivery note line does not point at its order line; the item is what the two share. */
    private static SalesOrderItem orderLineFor(SalesOrder so, DeliveryNoteItem item) {
        if (so == null || so.getItems() == null || item.getInventoryItem() == null) return null;
        int itemId = item.getInventoryItem().getInventoryItemId();
        return so.getItems().stream()
                .filter(l -> l.getInventoryItem() != null && l.getInventoryItem().getInventoryItemId() == itemId)
                .findFirst()
                .orElse(null);
    }

    /** The line's own GST rate, or the order's single rate on orders that were taxed at the header. */
    private static BigDecimal gstRateOf(SalesOrderItem sold, SalesOrder so) {
        BigDecimal igst = nz(sold.getIgstRate());
        BigDecimal split = nz(sold.getCgstRate()).add(nz(sold.getSgstRate()));
        BigDecimal rate = igst.signum() > 0 ? igst : split;
        return (rate.signum() > 0 ? rate : nz(so.getTaxPercentage())).stripTrailingZeros();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** One challan line with its value. The three figures are null for an item the order does not carry. */
    @Getter
    public static final class Line {
        private final DeliveryNoteItem item;
        private final BigDecimal rate;
        private final BigDecimal gstRate;
        private final BigDecimal taxableValue;

        Line(DeliveryNoteItem item, BigDecimal rate, BigDecimal gstRate, BigDecimal taxableValue) {
            this.item = item;
            this.rate = rate;
            this.gstRate = gstRate;
            this.taxableValue = taxableValue;
        }
    }
}
