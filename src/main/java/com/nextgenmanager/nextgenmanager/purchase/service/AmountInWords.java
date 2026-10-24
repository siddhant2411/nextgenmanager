package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.common.model.DocumentCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Converts an amount to English words.
 * e.g. 1,50,500.75 → "Rupees One Lakh Fifty Thousand Five Hundred and Paise Seventy-Five Only"
 *
 * <p>Rupees are counted in lakhs and crores; any other currency in thousands and millions, as its
 * buyer would read it: 1,50,500.75 USD → "US Dollars One Hundred Fifty Thousand Five Hundred and
 * Cents Seventy Five Only".
 */
public final class AmountInWords {

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWords() {}

    public static String convert(BigDecimal amount) {
        return convert(amount, "INR");
    }

    public static String convert(BigDecimal amount, String currencyCode) {
        if (amount == null) return "";
        DocumentCurrency currency = DocumentCurrency.of(currencyCode);
        boolean indian = currency == DocumentCurrency.INR;
        String major = currency != null ? currency.getMajorInWords() : currencyCode.trim().toUpperCase(Locale.ROOT);
        String minor = currency != null ? currency.getMinorInWords() : null;
        int decimals = currency != null ? currency.getDecimals() : 2;

        amount = amount.setScale(minor != null ? decimals : 0, RoundingMode.HALF_UP);
        long whole = amount.longValue();
        int fraction = minor == null ? 0
                : amount.subtract(BigDecimal.valueOf(whole)).movePointRight(decimals).intValue();

        StringBuilder sb = new StringBuilder(major).append(' ');
        sb.append(whole == 0 ? "Zero" : indian ? inWords(whole) : inWordsInternational(whole));
        if (fraction > 0) {
            sb.append(" and ").append(minor).append(' ').append(inWords(fraction));
        }
        sb.append(" Only");
        return sb.toString();
    }

    private static String inWords(long n) {
        if (n == 0) return "";
        if (n < 20)  return ONES[(int) n];
        if (n < 100) return TENS[(int)(n / 10)] + (n % 10 != 0 ? " " + ONES[(int)(n % 10)] : "");
        if (n < 1_000)
            return ONES[(int)(n / 100)] + " Hundred" + (n % 100 != 0 ? " " + inWords(n % 100) : "");
        if (n < 1_00_000)     // thousands
            return inWords(n / 1_000) + " Thousand" + (n % 1_000 != 0 ? " " + inWords(n % 1_000) : "");
        if (n < 1_00_00_000L) // lakhs
            return inWords(n / 1_00_000) + " Lakh" + (n % 1_00_000 != 0 ? " " + inWords(n % 1_00_000) : "");
        // crores
        return inWords(n / 1_00_00_000L) + " Crore" + (n % 1_00_00_000L != 0 ? " " + inWords(n % 1_00_00_000L) : "");
    }

    private static String inWordsInternational(long n) {
        if (n < 1_000) return inWords(n);
        if (n < 1_000_000)
            return inWords(n / 1_000) + " Thousand" + (n % 1_000 != 0 ? " " + inWords(n % 1_000) : "");
        if (n < 1_000_000_000L)
            return inWordsInternational(n / 1_000_000) + " Million"
                    + (n % 1_000_000 != 0 ? " " + inWordsInternational(n % 1_000_000) : "");
        return inWordsInternational(n / 1_000_000_000L) + " Billion"
                + (n % 1_000_000_000L != 0 ? " " + inWordsInternational(n % 1_000_000_000L) : "");
    }
}
