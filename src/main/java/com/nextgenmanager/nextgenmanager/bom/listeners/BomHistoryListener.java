package com.nextgenmanager.nextgenmanager.bom.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.bom.events.BomStatusChangedEvent;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomHistory;
import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import com.nextgenmanager.nextgenmanager.bom.repository.BomHistoryRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BomHistoryListener {

    private final BomHistoryRepository repo;
    private final BomRepository bomRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public BomHistoryListener(BomHistoryRepository repo,
                              BomRepository bomRepository,
                              ObjectMapper objectMapper) {
        this.repo = repo;
        this.bomRepository = bomRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void on(BomStatusChangedEvent event) {

        // Only maintain BOM History when an APPROVED version is created
        if (event.getNewStatus() != BomStatus.APPROVED) {
            return;
        }

        // Fetch the full BOM to store as a JSON snapshot
        Bom bom = bomRepository.findById(event.getBomId())
                .orElse(null);

        if (bom == null) {
            return; // or log warning
        }

        BomHistory history = new BomHistory();
        history.setBomId(bom.getId());
        history.setVersionNumber(bom.getVersionNumber());
        history.setRevision(bom.getRevision());
        history.setChangedAt(event.getOccurredAt());
        history.setChangedBy("SYSTEM"); // replace with actual username if available
        history.setChangeType("VERSION_BUMP");

        try {
            // Convert the full BOM object to JSON and store it
            String snapshotJson = objectMapper.writeValueAsString(bom);
            history.setSnapshotJson(snapshotJson);
        } catch (Exception ex) {
            // If JSON fails, store error but still save version bump
            history.setSnapshotJson("{\"error\": \"failed to serialize\"}");
        }

        // Human-readable summary
        history.setChangeSummary(
                "BOM version updated to V" + bom.getVersionNumber() +
                        " and revision " + bom.getRevision()
        );

        repo.save(history);
    }
}
