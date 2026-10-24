package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.common.service.ExchangeRates;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

/**
 * The rate a new document in a currency starts with: the one last entered on a quotation or sales
 * order in it. There is no rate feed; the user always sees the rate and can change it.
 */
@Service
@RequiredArgsConstructor
public class LatestExchangeRate {

    private final QuotationRepository quotationRepository;
    private final SalesOrderRepository salesOrderRepository;

    /** Empty for rupees, and for a currency no document has used yet. */
    public Optional<BigDecimal> forCurrency(String currency) {
        if (currency == null || currency.isBlank() || ExchangeRates.isBase(currency)) return Optional.empty();
        String code = currency.trim();
        Optional<Quotation> q = quotationRepository
                .findFirstByCurrencyIgnoreCaseAndExchangeRateNotAndDeletedDateIsNullOrderByCreationDateDesc(code, BigDecimal.ONE);
        Optional<SalesOrder> so = salesOrderRepository
                .findFirstByCurrencyIgnoreCaseAndExchangeRateNotAndDeletedDateIsNullOrderByCreationDateDesc(code, BigDecimal.ONE);
        if (q.isEmpty()) return so.map(SalesOrder::getExchangeRate);
        if (so.isEmpty()) return q.map(Quotation::getExchangeRate);
        return Optional.of(later(q.get().getCreationDate(), so.get().getCreationDate())
                ? q.get().getExchangeRate() : so.get().getExchangeRate());
    }

    private static boolean later(Date a, Date b) {
        if (a == null) return false;
        return b == null || a.after(b);
    }
}
