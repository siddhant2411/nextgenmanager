package com.nextgenmanager.nextgenmanager.purchase.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a BigDecimal rupee amount to Indian-English words.
 * e.g. 1,50,500.75 → "Rupees One Lakh Fifty Thousand Five Hundred and Paise Seventy-Five Only"
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
        if (amount == null) return "";
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = amount.longValue();
        int paise   = amount.subtract(BigDecimal.valueOf(rupees)).multiply(BigDecimal.valueOf(100))
                            .intValue();

        StringBuilder sb = new StringBuilder("Rupees ");
        if (rupees == 0) {
            sb.append("Zero");
        } else {
            sb.append(inWords(rupees));
        }
        if (paise > 0) {
            sb.append(" and Paise ").append(inWords(paise));
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
}
