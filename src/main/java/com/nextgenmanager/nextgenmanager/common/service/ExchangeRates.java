package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import com.nextgenmanager.nextgenmanager.common.model.DocumentCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Rupees per one unit of a document's currency, and the conversions that use it.
 *
 * <p>A quotation or sales order is written in the customer's currency, but the books, GST returns,
 * item prices, costs and minimum prices are all in rupees. The rate is set on the document, copied
 * onto the invoice when it is raised, and every rupee figure taken from a foreign-currency document
 * goes through {@link #toBase}. A rupee document always has a rate of exactly 1.
 */
public final class ExchangeRates {

    private ExchangeRates() {
    }

    public static boolean isBase(String currency) {
        return DocumentCurrency.of(currency) == DocumentCurrency.INR;
    }

    /** The rate a document is saved with: 1 for rupees; for any other currency, the rate given, which must be above zero. */
    public static BigDecimal normalise(String currency, BigDecimal rate) {
        if (isBase(currency)) return BigDecimal.ONE;
        if (rate == null || rate.signum() <= 0) {
            String code = currency.trim().toUpperCase(Locale.ROOT);
            throw new BusinessException("Enter the exchange rate for " + code
                    + ": how many rupees one " + code + " is worth.");
        }
        return rate;
    }

    /** A rate as stored, read safely: a missing or nonsensical rate counts as 1. */
    public static BigDecimal orOne(BigDecimal rate) {
        return rate != null && rate.signum() > 0 ? rate : BigDecimal.ONE;
    }

    /** An amount in the document's currency, in rupees, to the paisa. */
    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(orOne(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    /** A rupee amount, such as an item's selling price, in the document's currency, to the cent. */
    public static BigDecimal fromBase(BigDecimal rupees, BigDecimal rate) {
        if (rupees == null) return null;
        return rupees.divide(orOne(rate), 2, RoundingMode.HALF_UP);
    }
}
