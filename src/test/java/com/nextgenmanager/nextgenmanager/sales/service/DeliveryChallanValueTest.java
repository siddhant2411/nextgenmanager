package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNoteItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryChallanValueTest {

    @Test
    void aPartDeliveryIsValuedOnWhatIsOnTheChallanNotOnTheOrder() {
        SalesOrder so = order(TaxType.CGST_SGST, "0", sold(1, "10", "1000.00", "9", "9", "0"));
        DeliveryChallanValue value = DeliveryChallanValue.of(challan(so, delivered(1, "4")));

        assertThat(value.getTaxableValue()).isEqualByComparingTo("4000.00");
        assertThat(value.getCgst()).isEqualByComparingTo("360.00");
        assertThat(value.getSgst()).isEqualByComparingTo("360.00");
        assertThat(value.getIgst()).isEqualByComparingTo("0");
        assertThat(value.getTotalValue()).isEqualByComparingTo("4720.00");
        assertThat(value.getLines().get(0).getGstRate()).isEqualByComparingTo("18");
    }

    @Test
    void theOrdersDiscountComesOffBeforeTheValueIsStated() {
        SalesOrder so = order(TaxType.IGST, "5", sold(1, "10", "1000.00", "0", "0", "18"));
        DeliveryChallanValue value = DeliveryChallanValue.of(challan(so, delivered(1, "2.5")));

        assertThat(value.getLines().get(0).getRate()).isEqualByComparingTo("950.00");
        assertThat(value.getTaxableValue()).isEqualByComparingTo("2375.00");
        assertThat(value.getIgst()).isEqualByComparingTo("427.50");
        assertThat(value.getCgst()).isEqualByComparingTo("0");
    }

    @Test
    void anOrderTaxedAtTheHeaderUsesTheHeaderRate() {
        SalesOrder so = order(TaxType.CGST_SGST, "0", sold(1, "1", "200.00", "0", "0", "0"));
        so.setTaxPercentage(new BigDecimal("12"));
        DeliveryChallanValue value = DeliveryChallanValue.of(challan(so, delivered(1, "1")));

        assertThat(value.getCgst()).isEqualByComparingTo("12.00");
        assertThat(value.getSgst()).isEqualByComparingTo("12.00");
    }

    @Test
    void anItemTheOrderDoesNotCarryIsShownUnvaluedAndNoTotalIsClaimed() {
        SalesOrder so = order(TaxType.CGST_SGST, "0", sold(1, "10", "1000.00", "9", "9", "0"));
        DeliveryChallanValue value = DeliveryChallanValue.of(challan(so, delivered(1, "1"), delivered(2, "1")));

        assertThat(value.isComplete()).isFalse();
        assertThat(value.getLines()).hasSize(2);
        assertThat(value.getLines().get(1).getTaxableValue()).isNull();
    }

    private static SalesOrder order(TaxType taxType, String discountPct, SalesOrderItem... lines) {
        SalesOrder so = new SalesOrder();
        so.setTaxType(taxType);
        so.setDiscountPercentage(new BigDecimal(discountPct));
        so.setItems(List.of(lines));
        return so;
    }

    private static SalesOrderItem sold(int itemId, String qty, String price, String cgst, String sgst, String igst) {
        SalesOrderItem line = new SalesOrderItem();
        line.setInventoryItem(item(itemId));
        line.setQty(new BigDecimal(qty));
        line.setPricePerUnit(new BigDecimal(price));
        line.setCgstRate(new BigDecimal(cgst));
        line.setSgstRate(new BigDecimal(sgst));
        line.setIgstRate(new BigDecimal(igst));
        return line;
    }

    private static DeliveryNote challan(SalesOrder so, DeliveryNoteItem... items) {
        DeliveryNote dn = new DeliveryNote();
        dn.setSalesOrder(so);
        dn.setItems(List.of(items));
        return dn;
    }

    private static DeliveryNoteItem delivered(int itemId, String qty) {
        DeliveryNoteItem item = new DeliveryNoteItem();
        item.setInventoryItem(item(itemId));
        item.setQuantityDelivered(new BigDecimal(qty));
        return item;
    }

    private static InventoryItem item(int id) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        return item;
    }
}
