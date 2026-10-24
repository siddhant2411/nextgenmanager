package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRatesTest {

    @Test
    void aRupeeDocumentIsAlwaysAtOne() {
        assertThat(ExchangeRates.normalise("INR", new BigDecimal("84"))).isEqualByComparingTo("1");
        assertThat(ExchangeRates.normalise(null, null)).isEqualByComparingTo("1");
        assertThat(ExchangeRates.normalise(" inr ", null)).isEqualByComparingTo("1");
    }

    @Test
    void anyOtherCurrencyNeedsARateAboveZero() {
        assertThat(ExchangeRates.normalise("USD", new BigDecimal("84.25"))).isEqualByComparingTo("84.25");
        assertThatThrownBy(() -> ExchangeRates.normalise("USD", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("exchange rate for USD");
        assertThatThrownBy(() -> ExchangeRates.normalise("eur", BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("EUR");
    }

    @Test
    void convertsBothWaysToTheSmallestUnit() {
        assertThat(ExchangeRates.toBase(new BigDecimal("1180.00"), new BigDecimal("84.25"))).isEqualByComparingTo("99415.00");
        assertThat(ExchangeRates.fromBase(new BigDecimal("450.00"), new BigDecimal("84"))).isEqualByComparingTo("5.36");
        assertThat(ExchangeRates.toBase(null, new BigDecimal("84"))).isEqualByComparingTo("0");
        assertThat(ExchangeRates.fromBase(null, new BigDecimal("84"))).isNull();
    }

    @Test
    void aMissingRateCountsAsOne() {
        assertThat(ExchangeRates.toBase(new BigDecimal("10.00"), null)).isEqualByComparingTo("10.00");
        assertThat(ExchangeRates.fromBase(new BigDecimal("10.00"), BigDecimal.ZERO)).isEqualByComparingTo("10.00");
    }
}
