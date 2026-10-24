package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderProfitabilityDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Margin compares like with like: costs are in rupees, so a foreign-currency order's revenue is taken at its rate. */
@ExtendWith(MockitoExtension.class)
class SalesOrderProfitabilityCurrencyTest {

    @Mock private SalesOrderRepository salesOrderRepository;
    @InjectMocks private SalesOrderServiceImpl service;

    private SalesOrder order(String currency, String rate, String taxable) {
        ProductFinanceSettings finance = new ProductFinanceSettings();
        finance.setStandardCost(6000d);              // ₹6,000 a unit
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(7);
        item.setProductFinanceSettings(finance);

        SalesOrderItem line = new SalesOrderItem();
        line.setInventoryItem(item);
        line.setQty(new BigDecimal("10"));            // ₹60,000 of cost, nothing dispatched yet

        SalesOrder so = new SalesOrder();
        so.setId(1L);
        so.setCurrency(currency);
        so.setExchangeRate(new BigDecimal(rate));
        so.setTaxableValue(new BigDecimal(taxable));
        so.setItems(List.of(line));
        return so;
    }

    @Test
    void aDollarOrderIsMeasuredInRupeesAtItsRate() {
        // $1,000 at ₹84 = ₹84,000 of revenue against ₹60,000 of cost
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order("USD", "84", "1000.00")));

        SalesOrderProfitabilityDto p = service.getProfitability(1L);

        assertThat(p.getTotalRevenue()).isEqualByComparingTo("84000.00");
        assertThat(p.getTotalActualCost()).isEqualByComparingTo("60000");
        assertThat(p.getGrossProfit()).isEqualByComparingTo("24000.00");
        assertThat(p.getGrossMarginPercentage()).isEqualByComparingTo("28.57");
    }

    @Test
    void aRupeeOrderIsUnchanged() {
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order("INR", "1", "84000.00")));

        SalesOrderProfitabilityDto p = service.getProfitability(1L);

        assertThat(p.getTotalRevenue()).isEqualByComparingTo("84000.00");
        assertThat(p.getGrossMarginPercentage()).isEqualByComparingTo("28.57");
    }
}
