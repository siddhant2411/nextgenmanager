package com.nextgenmanager.nextgenmanager.sales.analytics.controller;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import com.nextgenmanager.nextgenmanager.sales.analytics.service.SalesAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Revenue Desk reads. One window in, one snapshot out. */
@RestController
@RequestMapping("/api/sales/analytics")
@RequiresSalesAccess
@RequiredArgsConstructor
public class SalesAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(SalesAnalyticsController.class);

    /**
     * The endpoint takes a window and nothing else.
     *
     * <p>Same strict treatment the pipeline endpoints get, for the same reason: a mistyped
     * {@code ?form=2026-04-01} would bind nothing, silently fall back to the default period, and
     * render this month's revenue under a heading claiming the year. A wrong number beneath a
     * correct-looking label is worse than an error.
     */
    private static final Set<String> PARAMS = Set.of("preset", "from", "to");

    private final SalesAnalyticsService salesAnalyticsService;

    @GetMapping
    public ResponseEntity<?> getSalesAnalytics(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        try {
            rejectUnknownParams(request);
            return ResponseEntity.ok(salesAnalyticsService.getAnalytics(CrmPeriod.resolve(preset, from, to)));
        } catch (InvalidDataException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error while fetching sales analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch sales analytics: " + e.getMessage()));
        }
    }

    private static void rejectUnknownParams(HttpServletRequest request) {
        List<String> unknown = request.getParameterMap().keySet().stream()
                .filter(name -> !PARAMS.contains(name))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidDataException("Unknown query parameter(s): " + String.join(", ", unknown)
                    + ". Supported: " + PARAMS.stream().sorted().collect(Collectors.joining(", ")));
        }
    }
}
