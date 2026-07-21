package com.nextgenmanager.nextgenmanager.bom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly multi-level standard-cost roll-up. Recomputes every manufactured item's fully-loaded
 * standardCost so parent BOM costs pick up their sub-assemblies' conversion + overhead.
 */
@Component
public class StandardCostRollupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StandardCostRollupScheduler.class);

    @Autowired
    private StandardCostRollupService standardCostRollupService;

    // 02:00 every day (server time). Adjust via cron if needed.
    @Scheduled(cron = "0 0 2 * * *")
    public void nightlyRollUp() {
        try {
            int count = standardCostRollupService.rollUpAllStandardCosts();
            logger.info("Nightly standard-cost roll-up complete: {} items recomputed", count);
        } catch (Exception e) {
            logger.error("Nightly standard-cost roll-up failed", e);
        }
    }
}
