package com.nextgenmanager.nextgenmanager.purchase.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmountInWordsTest {

    @Test
    void rupeesInLakhsAndCrores() {
        assertEquals("Rupees One Lakh Fifty Thousand Five Hundred and Paise Seventy Five Only",
                AmountInWords.convert(new BigDecimal("150500.75")));
        assertEquals("Rupees Two Crore Only", AmountInWords.convert(new BigDecimal("20000000"), "INR"));
    }

    @Test
    void otherCurrenciesInThousandsAndMillions() {
        assertEquals("US Dollars One Hundred Fifty Thousand Five Hundred and Cents Seventy Five Only",
                AmountInWords.convert(new BigDecimal("150500.75"), "USD"));
        assertEquals("Euros Two Million Three Hundred Thousand Only",
                AmountInWords.convert(new BigDecimal("2300000"), "eur"));
        assertEquals("Pounds Sterling Ten and Pence Five Only", AmountInWords.convert(new BigDecimal("10.05"), "GBP"));
    }

    @Test
    void yenHasNoFraction() {
        assertEquals("Japanese Yen One Thousand Two Hundred Thirty Five Only",
                AmountInWords.convert(new BigDecimal("1234.50"), "JPY"));
    }

    @Test
    void blankMeansRupees() {
        assertEquals("Rupees Five Only", AmountInWords.convert(new BigDecimal("5"), null));
    }
}
