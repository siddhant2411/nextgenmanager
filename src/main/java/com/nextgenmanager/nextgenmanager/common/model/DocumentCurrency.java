package com.nextgenmanager.nextgenmanager.common.model;

import java.util.Locale;

/**
 * The currencies a document can be raised in: the rupee and the ones India trades in most.
 *
 * <p>The UI offers the same list (ui/src/components/common/currency.js). A code stored before this list
 * existed, or one not on it, still prints: as its own code, with no sign.
 */
public enum DocumentCurrency {

    INR("₹", "Indian Rupee", "Rupees", "Paise", 2),
    USD("$", "US Dollar", "US Dollars", "Cents", 2),
    EUR("€", "Euro", "Euros", "Cents", 2),
    GBP("£", "British Pound", "Pounds Sterling", "Pence", 2),
    AED("AED", "UAE Dirham", "UAE Dirhams", "Fils", 2),
    SAR("SAR", "Saudi Riyal", "Saudi Riyals", "Halalas", 2),
    CNY("CN¥", "Chinese Yuan", "Chinese Yuan", "Fen", 2),
    JPY("JP¥", "Japanese Yen", "Japanese Yen", null, 0),
    SGD("S$", "Singapore Dollar", "Singapore Dollars", "Cents", 2),
    HKD("HK$", "Hong Kong Dollar", "Hong Kong Dollars", "Cents", 2),
    AUD("A$", "Australian Dollar", "Australian Dollars", "Cents", 2),
    CAD("C$", "Canadian Dollar", "Canadian Dollars", "Cents", 2),
    CHF("CHF", "Swiss Franc", "Swiss Francs", "Centimes", 2),
    RUB("₽", "Russian Ruble", "Russian Rubles", "Kopecks", 2);

    private final String symbol;
    private final String displayName;
    private final String majorInWords;
    /** Null for a currency with no fractional unit in use. */
    private final String minorInWords;
    private final int decimals;

    DocumentCurrency(String symbol, String displayName, String majorInWords, String minorInWords, int decimals) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.majorInWords = majorInWords;
        this.minorInWords = minorInWords;
        this.decimals = decimals;
    }

    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public String getMajorInWords() { return majorInWords; }
    public String getMinorInWords() { return minorInWords; }
    public int getDecimals() { return decimals; }

    /** The currency for a stored code; blank means rupees, as every document defaults to. */
    public static DocumentCurrency of(String code) {
        if (code == null || code.isBlank()) return INR;
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** What a column heading or total prints for a code: its sign, or the code itself when it is not on the list. */
    public static String signOf(String code) {
        DocumentCurrency c = of(code);
        return c != null ? c.symbol : code.trim().toUpperCase(Locale.ROOT);
    }
}
