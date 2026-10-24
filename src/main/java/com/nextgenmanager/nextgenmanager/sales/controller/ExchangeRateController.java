package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import com.nextgenmanager.nextgenmanager.sales.service.LatestExchangeRate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** The rate a quotation or sales order form offers when a foreign currency is picked. */
@RestController
@RequestMapping("/api/exchange-rates")
@RequiresSalesAccess
@RequiredArgsConstructor
public class ExchangeRateController {

    private final LatestExchangeRate latestExchangeRate;

    /** {"currency": "USD", "rate": 84.25}, or a null rate when none has been entered yet. */
    @GetMapping("/latest")
    public Map<String, Object> latest(@RequestParam String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("currency", currency);
        body.put("rate", latestExchangeRate.forCurrency(currency).orElse(null));
        return body;
    }
}
