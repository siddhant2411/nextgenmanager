package com.nextgenmanager.nextgenmanager.common.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.model.AuditTrailEntry;
import com.nextgenmanager.nextgenmanager.common.audit.repository.AuditTrailRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditTrailServiceImplTest {

    private final AuditTrailServiceImpl service =
            new AuditTrailServiceImpl(mock(AuditTrailRepository.class), new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void changesListOnlyWhatDiffers_withOldAndNewValues() {
        Map<String, Object> changed = AuditTrailService.changes(
                map("name", "Bank", "openingBalance", new BigDecimal("1000"), "bankAccount", true),
                map("name", "Bank", "openingBalance", new BigDecimal("5000"), "bankAccount", false));

        assertThat(changed).containsOnlyKeys("openingBalance", "bankAccount");
        assertThat(changed.get("openingBalance")).isEqualTo(map("from", new BigDecimal("1000"), "to", new BigDecimal("5000")));
    }

    @Test
    void anAmountWrittenWithMoreDecimalsIsNotAChange() {
        assertThat(AuditTrailService.changes(
                map("openingBalance", new BigDecimal("100")),
                map("openingBalance", new BigDecimal("100.00")))).isEmpty();
    }

    @Test
    void theEntryNamesTheSignedInUser_unlessTheEventNamesSomeoneElse() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("arjun", null, List.of()));

        AuditTrailEntry mine = service.toEntry(AuditEvent.of(AuditActions.UPDATE, AuditActions.LEDGER, 1L));
        AuditTrailEntry theirs = service.toEntry(AuditEvent.of(AuditActions.APPROVE, AuditActions.VOUCHER, 2L).by("meera"));

        assertThat(mine.getUsername()).isEqualTo("arjun");
        assertThat(theirs.getUsername()).isEqualTo("meera");
    }

    @Test
    void detailsAreStoredAsJson_andALongSummaryIsCutToFit() {
        AuditTrailEntry e = service.toEntry(AuditEvent.of(AuditActions.UPDATE, AuditActions.LEDGER, 1L)
                .summary("x".repeat(800))
                .details(map("openingBalance", map("from", 0, "to", 25000))));

        assertThat(e.getDetails()).isEqualTo("{\"openingBalance\":{\"from\":0,\"to\":25000}}");
        assertThat(e.getSummary()).hasSize(500).endsWith("…");
        assertThat(e.getEventTime()).isNotNull();
        assertThat(e.getUsername()).isNull(); // no one signed in
    }
}
